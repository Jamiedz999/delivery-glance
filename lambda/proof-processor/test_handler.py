"""The Lambda's acceptance-critical guarantee, tested on the image bytes alone.

The epic's floor names one assertion explicitly: EXIF/GPS is stripped, asserted against a fixture
that carries GPS. That is what this file proves, without S3 or a Lambda runtime — the scrub is a
pure function of bytes, so it is tested as one.
"""

from __future__ import annotations

import io

import piexif
import pytest
from PIL import Image

from handler import scrub_image


def _jpeg_carrying_gps() -> bytes:
    image = Image.new("RGB", (200, 150), (120, 30, 30))
    exif = {
        "0th": {piexif.ImageIFD.Make: b"TestCam", piexif.ImageIFD.Model: b"Model X"},
        "GPS": {
            piexif.GPSIFD.GPSLatitudeRef: b"N",
            piexif.GPSIFD.GPSLatitude: [(51, 1), (30, 1), (0, 1)],
            piexif.GPSIFD.GPSLongitudeRef: b"W",
            piexif.GPSIFD.GPSLongitude: [(0, 1), (7, 1), (40, 1)],
        },
    }
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", exif=piexif.dump(exif))
    return buffer.getvalue()


def test_the_fixture_really_carries_gps():
    # If this ever stops being true, the strip assertion below proves nothing.
    assert piexif.load(_jpeg_carrying_gps())["GPS"], "fixture should carry GPS to be worth stripping"


def test_scrub_strips_gps_and_every_other_exif_tag():
    scrubbed = scrub_image(_jpeg_carrying_gps())

    assert piexif.load(scrubbed.clean_bytes)["GPS"] == {}
    assert Image.open(io.BytesIO(scrubbed.clean_bytes))._getexif() is None
    assert scrubbed.content_type == "image/jpeg"
    assert len(scrubbed.content_hash) == 64


def test_scrub_writes_a_bounded_thumbnail():
    scrubbed = scrub_image(_jpeg_carrying_gps())

    thumbnail = Image.open(io.BytesIO(scrubbed.thumbnail_bytes))
    assert max(thumbnail.size) <= 320


def test_scrub_preserves_a_png_signature_without_metadata():
    canvas = Image.new("RGBA", (300, 120), (255, 255, 255, 0))
    buffer = io.BytesIO()
    canvas.save(buffer, format="PNG")

    scrubbed = scrub_image(buffer.getvalue())

    assert scrubbed.content_type == "image/png"
    assert Image.open(io.BytesIO(scrubbed.clean_bytes)).size == (300, 120)


def test_scrub_rejects_anything_that_is_not_an_image():
    with pytest.raises((ValueError, OSError)):
        scrub_image(b"this is plainly not an image")
