"""Off-band notification consumer Lambda (Issue 51).

An SQS message carrying a bare transition id triggers this function. It asks the application whether
to send (``begin``), and only when told to proceed does it render the state-derived message and send
it — SES for email, SNS for SMS — then confirm the send (``sent``). ``ALREADY_SENT``, ``SUPPRESSED``
and ``UNKNOWN`` send nothing, so a redelivery, an unsubscribe and an unknown id are all no-ops. A
send that fails raises, so SQS retries and finally parks the message in the dead-letter queue without
ever touching the delivery command that produced it.

All the exactly-once and suppression reasoning lives in the application, keyed on the transition id;
this function holds no state. The message is derived from the delivery state alone, reusing the
tracking page's own vocabulary (`web/src/track/copy.ts` ``STATE_COPY``), and never names a dispatch
internal — matching to a courier, a decline, a reassignment. Cancelled says the delivery was
cancelled and nothing further, so it cannot imply a retry nobody arranged.

Environment:
    APP_CALLBACK_URL     Base URL of the application. begin/sent POST to
                         ``{APP_CALLBACK_URL}/api/internal/notifications/{begin,sent}``.
    APP_CALLBACK_TOKEN   Shared bearer token the application compares to authorize the callbacks.
    NOTIFY_EMAIL_SOURCE  The verified SES sender address for email notifications.
"""

from __future__ import annotations

import json
import os
import urllib.request
from dataclasses import dataclass

import boto3

# The public copy for the four notify-worthy states, kept identical to the tracking page's
# STATE_COPY. A fifth state is not notify-worthy (AWAITING_COURIER never notifies), so it is absent
# here on purpose: a transition that reaches this table is one the outbox already decided to send.
STATE_COPY = {
    "ASSIGNED": ("A courier has been assigned", "They are on their way to collect your delivery."),
    "IN_TRANSIT": ("Your delivery is on the way", "The courier is heading to the handoff address."),
    "DELIVERED": ("Delivered", "Your delivery was handed over."),
    "CANCELLED": ("This delivery was cancelled", "Nothing further is scheduled for it."),
}

# How a Recipient turns the messages off. It is the same opt-out the tracking page offers, named in
# words rather than a one-click link because the capability to unsubscribe is the tracking link
# itself, which the message deliberately does not carry.
UNSUBSCRIBE_NOTE = "To stop these updates, open your tracking link and turn notifications off."


@dataclass(frozen=True)
class RenderedMessage:
    """One notification, rendered for both channels from the state and reference alone."""

    subject: str
    email_body: str
    sms_body: str


def render(next_state: str, reference: str) -> RenderedMessage:
    """Build the message for a state. Raises ``KeyError`` for a state that never notifies."""
    headline, next_step = STATE_COPY[next_state]
    subject = f"Update on delivery {reference}"
    email_body = f"{headline}\n\n{next_step}\n\n{UNSUBSCRIBE_NOTE}"
    sms_body = f"{reference}: {headline}. {next_step}"
    return RenderedMessage(subject=subject, email_body=email_body, sms_body=sms_body)


def lambda_handler(event, _context=None):
    """Process every SQS record the event carries. Each body is a bare transition id."""
    sent = 0
    for record in event.get("Records", []):
        if _process_one(record["body"].strip()):
            sent += 1
    return {"sent": sent}


def _process_one(transition_id: str) -> bool:
    decision = _begin(transition_id)
    if decision.get("status") != "PROCEED":
        # ALREADY_SENT, SUPPRESSED, UNKNOWN — the application has decided this transition needs no
        # send. Nothing here carries a channel or target, so a no-op never touches a provider.
        return False
    message = render(decision["nextState"], decision["deliveryReference"])
    _send(decision["channel"], decision["target"], message)
    _record_sent(transition_id)
    return True


def _send(channel: str, target: str, message: RenderedMessage) -> None:
    if channel == "EMAIL":
        _ses().send_email(
            Source=os.environ["NOTIFY_EMAIL_SOURCE"],
            Destination={"ToAddresses": [target]},
            Message={
                "Subject": {"Data": message.subject},
                "Body": {"Text": {"Data": message.email_body}},
            },
        )
    elif channel == "SMS":
        _sns().publish(PhoneNumber=target, Message=message.sms_body)
    else:
        raise ValueError(f"unknown channel: {channel}")


def _begin(transition_id: str) -> dict:
    return _callback("begin", transition_id)


def _record_sent(transition_id: str) -> None:
    _callback("sent", transition_id)


def _callback(step: str, transition_id: str) -> dict:
    base_url = os.environ["APP_CALLBACK_URL"].rstrip("/")
    request = urllib.request.Request(
        f"{base_url}/api/internal/notifications/{step}",
        data=json.dumps({"transitionId": transition_id}).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {os.environ['APP_CALLBACK_TOKEN']}",
        },
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        body = response.read()
        return json.loads(body) if body else {}


def _ses():
    return boto3.client("ses")


def _sns():
    return boto3.client("sns")
