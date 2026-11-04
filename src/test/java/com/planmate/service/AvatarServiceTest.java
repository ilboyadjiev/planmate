package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.dao.AvatarDao;
import com.planmate.dao.UserDAO;
import com.planmate.dto.User;
import com.planmate.dto.UserAvatar;
import com.planmate.exception.BusinessLogicException;

class AvatarServiceTest {

	private AvatarDao avatarDao;
	private UserDAO userDAO;
	private AvatarService service;
	private User alice;

	@BeforeAll
	static void headless() {
		System.setProperty("java.awt.headless", "true");
	}

	@BeforeEach
	void setUp() {
		avatarDao = mock(AvatarDao.class);
		userDAO = mock(UserDAO.class);
		service = new AvatarService();
		ReflectionTestUtils.setField(service, "avatarDao", avatarDao);
		ReflectionTestUtils.setField(service, "userDAO", userDAO);
		alice = new User();
		alice.setId(1L);
		when(userDAO.getUserById(1L)).thenReturn(alice);
	}

	/** A width x height picture: left half red, right half blue. */
	static byte[] picture(int width, int height, String format) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, width / 2, height);
		g.setColor(Color.BLUE);
		g.fillRect(width / 2, 0, width - width / 2, height);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private static BufferedImage read(byte[] jpeg) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(jpeg));
	}

	private static HttpStatus statusOf(Runnable r) {
		return assertThrows(BusinessLogicException.class, r::run).getHttpStatus();
	}

	@Test
	void storesASmallSquareJpegCroppedFromTheCentre() throws IOException {
		byte[] jpeg = AvatarService.normalise(picture(1200, 600, "png"));

		assertEquals((byte) 0xFF, jpeg[0]);
		assertEquals((byte) 0xD8, jpeg[1]);
		assertTrue(jpeg.length < 8 * 1024, "a stored picture is a few KB, was " + jpeg.length);
		BufferedImage image = read(jpeg);
		assertEquals(AvatarService.SIZE, image.getWidth());
		assertEquals(AvatarService.SIZE, image.getHeight());
		// The centre square of a red|blue picture is still red on the left and blue on the right
		Color left = new Color(image.getRGB(10, 64));
		Color right = new Color(image.getRGB(117, 64));
		assertTrue(left.getRed() > 200 && left.getBlue() < 60, "left " + left);
		assertTrue(right.getBlue() > 200 && right.getRed() < 60, "right " + right);
	}

	@Test
	void smallPicturesAreScaledUpToTheSameSize() throws IOException {
		BufferedImage image = read(AvatarService.normalise(picture(40, 50, "jpeg")));
		assertEquals(AvatarService.SIZE, image.getWidth());
		assertEquals(AvatarService.SIZE, image.getHeight());
	}

	@Test
	void dropsPhotoMetadata() throws IOException {
		// A JPEG with an EXIF segment (where cameras put GPS positions) right after the start marker
		byte[] photo = picture(300, 300, "jpeg");
		byte[] exif = "Exif\0\0GPS-SECRET-POSITION".getBytes(StandardCharsets.ISO_8859_1);
		ByteArrayOutputStream withExif = new ByteArrayOutputStream();
		withExif.write(photo, 0, 2);
		withExif.write(0xFF);
		withExif.write(0xE1);
		withExif.write((exif.length + 2) >> 8);
		withExif.write((exif.length + 2) & 0xFF);
		withExif.write(exif);
		withExif.write(photo, 2, photo.length - 2);

		String stored = new String(AvatarService.normalise(withExif.toByteArray()), StandardCharsets.ISO_8859_1);
		assertFalse(stored.contains("GPS-SECRET"));
		assertFalse(stored.contains("Exif"));
	}

	@Test
	void rejectsFilesThatAreNotPictures() {
		assertEquals(HttpStatus.BAD_REQUEST,
				statusOf(() -> AvatarService.normalise("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))));
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> AvatarService.normalise(new byte[0])));
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> AvatarService.normalise(null)));
	}

	@Test
	void rejectsTruncatedPictures() throws IOException {
		byte[] png = picture(200, 200, "png");
		byte[] cut = java.util.Arrays.copyOf(png, png.length / 3);
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> AvatarService.normalise(cut)));
	}

	@Test
	void rejectsUploadsOverTheLimit() {
		byte[] big = new byte[AvatarService.MAX_UPLOAD_BYTES + 1];
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, statusOf(() -> AvatarService.normalise(big)));
	}

	@Test
	void rejectsPicturesClaimingHugeDimensionsBeforeDecodingThem() throws IOException {
		// A tiny PNG whose header claims 50000 x 50000 pixels (2.5 billion): must not be decoded
		byte[] png = picture(10, 10, "png");
		writeInt(png, 16, 50_000);
		writeInt(png, 20, 50_000);
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> AvatarService.normalise(png)));
	}

	private static void writeInt(byte[] bytes, int at, int value) {
		bytes[at] = (byte) (value >> 24);
		bytes[at + 1] = (byte) (value >> 16);
		bytes[at + 2] = (byte) (value >> 8);
		bytes[at + 3] = (byte) value;
	}

	@Test
	void storeSavesThePictureAndGivesTheUserANewRandomKey() throws IOException {
		String first = service.store(1L, picture(300, 300, "png"));
		assertEquals(first, alice.getAvatarKey());
		assertTrue(first.matches("[A-Za-z0-9_-]{22}"), first);
		verify(avatarDao).save(eq(1L), any(byte[].class), any(Instant.class));
		verify(userDAO).updateUser(alice);

		String second = service.store(1L, picture(300, 300, "png"));
		assertNotEquals(first, second);
	}

	@Test
	void storeDoesNotSaveInvalidPictures() {
		assertThrows(BusinessLogicException.class, () -> service.store(1L, "nope".getBytes(StandardCharsets.UTF_8)));
		verify(avatarDao, never()).save(any(), any(), any());
		assertNull(alice.getAvatarKey());
	}

	@Test
	void imageNeedsTheCurrentKey() {
		byte[] jpeg = { 1, 2, 3 };
		alice.setAvatarKey("current-key-1234567890");
		when(avatarDao.get(1L)).thenReturn(new UserAvatar(1L, jpeg, Instant.now()));

		assertArrayEquals(jpeg, service.image(1L, "current-key-1234567890"));
		assertNull(service.image(1L, "an-old-key-1234567890"));
		assertNull(service.image(2L, "current-key-1234567890"));
		assertNull(service.image(1L, null));
	}

	@Test
	void removeDeletesThePictureAndTheKey() {
		alice.setAvatarKey("current-key-1234567890");
		service.remove(1L);
		verify(avatarDao).delete(1L);
		assertNull(alice.getAvatarKey());
	}
}
