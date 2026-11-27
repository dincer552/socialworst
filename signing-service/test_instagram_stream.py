import os
import unittest

from cryptography.fernet import Fernet

os.environ["SIGNING_ENCRYPTION_KEY"] = Fernet.generate_key().decode("ascii")

from instagram_stream import (
    issue_stream_token,
    parse_instagram_shortcode,
    read_stream_token,
    _upstream_headers,
)


class InstagramStreamTests(unittest.TestCase):
    def test_parse_reel_url_and_ignore_tracking_parameters(self):
        self.assertEqual(
            parse_instagram_shortcode(
                "https://www.instagram.com/reel/Df1ipAQNty9/?dmpr=MW1wbTh2aTFwMjVncg=="
            ),
            "Df1ipAQNty9",
        )

    def test_accept_reels_alias(self):
        self.assertEqual(
            parse_instagram_shortcode("https://instagram.com/reels/Abcde_123/"),
            "Abcde_123",
        )

    def test_reject_non_instagram_host(self):
        with self.assertRaises(ValueError):
            parse_instagram_shortcode("https://evil.example/reel/Df1ipAQNty9/")

    def test_reject_post_without_reel_video_path(self):
        with self.assertRaises(ValueError):
            parse_instagram_shortcode("https://www.instagram.com/p/Df1ipAQNty9/")

    def test_reject_http_url(self):
        with self.assertRaises(ValueError):
            parse_instagram_shortcode("http://www.instagram.com/reel/Df1ipAQNty9/")

    def test_signed_stream_token_round_trip(self):
        token = issue_stream_token("Df1ipAQNty9", now=1000)
        self.assertEqual(read_stream_token(token, now=1001), "Df1ipAQNty9")

    def test_expired_stream_token_is_rejected(self):
        token = issue_stream_token("Df1ipAQNty9", now=1000)
        with self.assertRaises(ValueError):
            read_stream_token(token, now=1000 + 15 * 60 + 1)

    def test_tampered_stream_token_is_rejected(self):
        token = issue_stream_token("Df1ipAQNty9", now=1000)
        altered = token[:-1] + ("0" if token[-1] != "0" else "1")
        with self.assertRaises(ValueError):
            read_stream_token(altered, now=1001)

    def test_range_header_is_forwarded(self):
        headers = _upstream_headers("bytes=1024-2047")
        self.assertEqual(headers["Range"], "bytes=1024-2047")
        self.assertEqual(headers["Accept-Encoding"], "identity")

    def test_invalid_range_header_is_rejected(self):
        from fastapi import HTTPException
        with self.assertRaises(HTTPException):
            _upstream_headers("bytes=0-1,5-10")


if __name__ == "__main__":
    unittest.main()
