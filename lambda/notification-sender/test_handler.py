"""The consumer's guarantees, tested without AWS.

Two things matter here and neither needs a broker or a provider: the message is honest and
state-derived (and cancelled implies no retry), and the begin/send/sent flow sends only on PROCEED
and re-raises a failed send so SQS can retry. The flow is tested by replacing the three seams —
begin, send, record-sent — with recorders.
"""

from __future__ import annotations

import pytest

import handler
from handler import RenderedMessage, render


def test_render_covers_every_notify_state_and_names_the_reference():
    for state in ("ASSIGNED", "IN_TRANSIT", "DELIVERED", "CANCELLED"):
        message = render(state, "DG-1001")
        assert "DG-1001" in message.subject
        assert message.email_body
        assert message.sms_body.startswith("DG-1001:")


def test_render_cancelled_implies_no_retry():
    message = render("CANCELLED", "DG-1001")

    assert "cancelled" in message.email_body.lower()
    lowered = message.email_body.lower()
    assert "retry" not in lowered
    assert "reschedul" not in lowered
    # Every message carries the way to stop them.
    assert "turn notifications off" in message.email_body.lower()


def test_render_refuses_a_state_that_never_notifies():
    with pytest.raises(KeyError):
        render("AWAITING_COURIER", "DG-1001")


class _Recorder:
    def __init__(self, decision):
        self.decision = decision
        self.sent = []
        self.confirmed = []

    def begin(self, transition_id):
        return self.decision

    def send(self, channel, target, message):
        self.sent.append((channel, target, message))

    def record_sent(self, transition_id):
        self.confirmed.append(transition_id)


def _wire(monkeypatch, recorder):
    monkeypatch.setattr(handler, "_begin", recorder.begin)
    monkeypatch.setattr(handler, "_send", recorder.send)
    monkeypatch.setattr(handler, "_record_sent", recorder.record_sent)


def test_proceed_sends_the_rendered_message_then_confirms(monkeypatch):
    recorder = _Recorder({"status": "PROCEED", "channel": "EMAIL", "target": "r@example.com",
                          "nextState": "IN_TRANSIT", "deliveryReference": "DG-1001"})
    _wire(monkeypatch, recorder)

    result = handler.lambda_handler({"Records": [{"body": "  t-1  "}]})

    assert result == {"sent": 1}
    assert recorder.sent == [("EMAIL", "r@example.com", render("IN_TRANSIT", "DG-1001"))]
    assert recorder.confirmed == ["t-1"]


@pytest.mark.parametrize("status", ["ALREADY_SENT", "SUPPRESSED", "UNKNOWN"])
def test_a_non_proceed_decision_sends_nothing(monkeypatch, status):
    recorder = _Recorder({"status": status})
    _wire(monkeypatch, recorder)

    result = handler.lambda_handler({"Records": [{"body": "t-1"}]})

    assert result == {"sent": 0}
    assert recorder.sent == []
    assert recorder.confirmed == []


def test_a_failed_send_propagates_and_is_not_confirmed(monkeypatch):
    recorder = _Recorder({"status": "PROCEED", "channel": "SMS", "target": "+15551234567",
                          "nextState": "DELIVERED", "deliveryReference": "DG-1001"})

    def failing_send(channel, target, message):
        raise RuntimeError("provider rejected the message")

    monkeypatch.setattr(handler, "_begin", recorder.begin)
    monkeypatch.setattr(handler, "_send", failing_send)
    monkeypatch.setattr(handler, "_record_sent", recorder.record_sent)

    with pytest.raises(RuntimeError):
        handler.lambda_handler({"Records": [{"body": "t-1"}]})
    # Never confirmed, so begin will PROCEED again on the redelivery rather than reporting it sent.
    assert recorder.confirmed == []


def test_render_result_is_a_value(monkeypatch):
    # The rendered message compares by value, which is what the send assertion above relies on.
    assert render("ASSIGNED", "DG-1") == RenderedMessage(
        subject="Update on delivery DG-1",
        email_body=render("ASSIGNED", "DG-1").email_body,
        sms_body=render("ASSIGNED", "DG-1").sms_body,
    )
