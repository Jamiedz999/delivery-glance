"""Proof-of-delivery processing Lambda (Issue 50).

An S3 ``ObjectCreated`` under ``raw/`` triggers this function. It validates that the upload is an
image, strips EXIF/GPS metadata, writes a scrubbed full copy and a thumbnail, and calls the
application back with the resulting keys and content hash. Anything that is not a valid image is
moved to ``quarantine/`` and reported rejected, so an invalid upload never reaches a read path.

The heavy work — decoding, re-encoding, thumbnailing — runs here rather than on the request thread,
which is the whole reason the feature exists: the browser uploads straight to S3, and this is what
happens next, asynchronously.

Environment:
    APP_CALLBACK_URL    Base URL of the application, e.g. https://app.example. The callback posts to
                        ``{APP_CALLBACK_URL}/api/internal/proof-processed``.
    APP_CALLBACK_TOKEN  Shared bearer token the application compares to authorize the callback.
    MAX_UPLOAD_BYTES    Largest object accepted (default 10485760). A larger object is rejected.
    THUMBNAIL_MAX_PX    Longest thumbnail edge in pixels (default 320).
"""

from __future__ import annotations

import hashlib
import io
import json
import os
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone

import boto3
from PIL import Image

RAW_PREFIX = "raw/"
CLEAN_PREFIX = "clean/"
THUMBNAIL_PREFIX = "thumb/"
QUARANTINE_PREFIX = "quarantine/"

# The formats a proof artifact may be. A photo is JPEG/WebP from a camera; a signature is a PNG from
# a canvas. Anything Pillow opens as something else is not proof.
ALLOWED_FORMATS = {"JPEG", "PNG", "WEBP"}

s3 = boto3.client("s3")


@dataclass(frozen=True)
class ScrubbedImage:
    """The result of scrubbing one upload: bytes with no metadata, a thumbnail, and a hash."""

    clean_bytes: bytes
    thumbnail_bytes: bytes
    content_hash: str
    content_type: str


def lambda_handler(event, _context=None):
    """Process every ``ObjectCreated`` record the event carries under ``raw/``."""
    for record in event.get("Records", []):
        bucket = record["s3"]["bucket"]["name"]
        raw_key = urllib.parse.unquote_plus(record["s3"]["object"]["key"])
        if not raw_key.startswith(RAW_PREFIX):
            continue
        _process_one(bucket, raw_key)
    return {"processed": len(event.get("Records", []))}


def _process_one(bucket: str, raw_key: str) -> None:
    max_bytes = int(os.environ.get("MAX_UPLOAD_BYTES", 10 * 1024 * 1024))
    obj = s3.get_object(Bucket=bucket, Key=raw_key)
    raw_bytes = obj["Body"].read()

    try:
        if len(raw_bytes) > max_bytes:
            raise ValueError("upload exceeds the maximum size")
        scrubbed = scrub_image(raw_bytes)
    except (ValueError, OSError, Image.DecompressionBombError):
        _quarantine(bucket, raw_key)
        _callback(raw_key=raw_key, outcome="REJECTED")
        return

    clean_key = _reprefix(raw_key, CLEAN_PREFIX)
    thumbnail_key = _reprefix(raw_key, THUMBNAIL_PREFIX)
    s3.put_object(Bucket=bucket, Key=clean_key, Body=scrubbed.clean_bytes,
                  ContentType=scrubbed.content_type)
    s3.put_object(Bucket=bucket, Key=thumbnail_key, Body=scrubbed.thumbnail_bytes,
                  ContentType=scrubbed.content_type)
    _callback(raw_key=raw_key, outcome="READY", clean_key=clean_key, thumbnail_key=thumbnail_key,
              content_hash=scrubbed.content_hash)


def scrub_image(raw_bytes: bytes) -> ScrubbedImage:
    """Validate, strip metadata and thumbnail an image, all in memory.

    The metadata strip is done by re-encoding from decoded pixels into a fresh image, not by asking
    the encoder to omit EXIF: a fresh image has no EXIF, GPS, XMP or thumbnail block to leak,
    whatever the source carried. Raises if the bytes are not an allowed image.
    """
    thumbnail_max = int(os.environ.get("THUMBNAIL_MAX_PX", 320))
    with Image.open(io.BytesIO(raw_bytes)) as opened:
        opened.load()
        image_format = opened.format
        if image_format not in ALLOWED_FORMATS:
            raise ValueError(f"unsupported image format: {image_format}")

        clean = Image.new(opened.mode, opened.size)
        clean.putdata(list(opened.getdata()))

        clean_bytes = _encode(clean, image_format)

        thumbnail = clean.copy()
        thumbnail.thumbnail((thumbnail_max, thumbnail_max))
        thumbnail_bytes = _encode(thumbnail, image_format)

    return ScrubbedImage(
        clean_bytes=clean_bytes,
        thumbnail_bytes=thumbnail_bytes,
        content_hash=hashlib.sha256(clean_bytes).hexdigest(),
        content_type=f"image/{image_format.lower()}",
    )


def _encode(image: Image.Image, image_format: str) -> bytes:
    buffer = io.BytesIO()
    # No exif/icc arguments are passed, and the pixels came from a fresh image, so nothing but the
    # picture is written.
    image.save(buffer, format=image_format)
    return buffer.getvalue()


def _quarantine(bucket: str, raw_key: str) -> None:
    quarantine_key = _reprefix(raw_key, QUARANTINE_PREFIX)
    s3.copy_object(Bucket=bucket, Key=quarantine_key,
                   CopySource={"Bucket": bucket, "Key": raw_key})
    s3.delete_object(Bucket=bucket, Key=raw_key)


def _reprefix(raw_key: str, prefix: str) -> str:
    return prefix + raw_key[len(RAW_PREFIX):]


def _callback(raw_key: str, outcome: str, clean_key: str | None = None,
              thumbnail_key: str | None = None, content_hash: str | None = None) -> None:
    base_url = os.environ["APP_CALLBACK_URL"].rstrip("/")
    payload = {
        "rawObjectKey": raw_key,
        "outcome": outcome,
        "cleanObjectKey": clean_key,
        "thumbnailObjectKey": thumbnail_key,
        "contentHash": content_hash,
        "processedAt": datetime.now(timezone.utc).isoformat(),
    }
    request = urllib.request.Request(
        f"{base_url}/api/internal/proof-processed",
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {os.environ['APP_CALLBACK_TOKEN']}",
        },
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        response.read()
