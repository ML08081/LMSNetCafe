import unittest
import tempfile
from io import BytesIO
from pathlib import Path
from unittest.mock import patch

import cv2
import numpy as np
from fastapi import UploadFile
from app.core.config import settings
from app.services.face_matcher import FaceMatcher


def upload(frame):
    ok, encoded = cv2.imencode('.jpg', frame)
    assert ok
    return UploadFile(file=BytesIO(encoded.tobytes()), filename='test.jpg')


class FaceMatcherTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.patch = patch.object(settings, 'feature_dir', Path(self.temp.name))
        self.patch.start()
        self.matcher = FaceMatcher()

    def tearDown(self):
        self.patch.stop()
        self.temp.cleanup()

    def fixture(self, name):
        path = Path(__file__).parent / 'fixtures' / name
        if not path.exists():
            self.fail('Run python tests/download_fixtures.py before integration tests')
        return cv2.imread(str(path))

    async def test_same_person_with_changed_brightness_and_different_person(self):
        lena = self.fixture('lena.jpg')
        # The original football sample has a tiny head. Crop its known sample region.
        other = cv2.resize(self.fixture('messi5.jpg')[65:160, 195:285], (360,380))
        await self.matcher.enroll(1, 11, upload(lena))
        same = await self.matcher.verify(1, 11, upload(cv2.convertScaleAbs(lena, alpha=1.08, beta=10)))
        different = await self.matcher.verify(1, 11, upload(other))
        self.assertTrue(same.matched, same)
        self.assertFalse(different.matched, different)
        identified = await self.matcher.identify(upload(lena))
        self.assertTrue(identified.matched)
        self.assertEqual(identified.subject_id, 1)
        self.assertEqual(identified.member_id, 11)
        self.matcher.remove(1)
        self.assertFalse((await self.matcher.identify(upload(lena))).matched)

    async def test_rejects_ambiguous_identity_and_legacy_profiles(self):
        image = self.fixture('lena.jpg')
        await self.matcher.enroll(1, 11, upload(image))
        await self.matcher.enroll(2, 12, upload(image))
        self.assertFalse((await self.matcher.identify(upload(image))).matched)
        np.savez_compressed(Path(self.temp.name)/'subject-3.npz', feature=np.ones(128), member_id=[13])
        with self.assertRaisesRegex(ValueError, '重新录入'):
            await self.matcher.verify(3, 13, upload(image))
        self.assertEqual(self.matcher.readiness()['profilesRequiringEnrollment'], 1)

    async def test_empty_corrupt_oversized_and_no_face_are_rejected(self):
        for data in (b'', b'invalid image', b'x' * (settings.maximum_image_bytes + 1)):
            with self.assertRaises(ValueError):
                await self.matcher.identify(UploadFile(file=BytesIO(data), filename='bad.jpg'))
        with self.assertRaises(ValueError):
            await self.matcher.identify(upload(np.zeros((400,400,3),dtype=np.uint8)))

    async def test_multiple_faces_are_rejected(self):
        image = cv2.resize(self.fixture('lena.jpg'), (450,450))
        with self.assertRaisesRegex(ValueError, '多张'):
            await self.matcher.identify(upload(np.concatenate((image,image),axis=1)))

    def test_missing_model_is_unready(self):
        with patch.object(settings, 'model_dir', Path(self.temp.name)/'missing'):
            result = self.matcher.readiness()
            self.assertFalse(result['ready'])
            self.assertIn('download_models.py', result['message'])


if __name__ == '__main__':
    unittest.main()
