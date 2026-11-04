package com.planmate.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.planmate.dao.AvatarDao;
import com.planmate.dao.UserDAO;
import com.planmate.dto.User;
import com.planmate.dto.UserAvatar;
import com.planmate.exception.BusinessLogicException;

/**
 * Profile pictures, kept as small as they can be while still looking sharp on phone screens: every upload is
 * cropped to a centred square and stored as one SIZE x SIZE JPEG of a few KB.
 *
 * The browser already shrinks the photo before uploading it, but nothing it sends is trusted: the server decodes
 * it (rejecting anything that is not a picture), and writes a fresh JPEG, which also drops all metadata such as
 * the GPS position a phone camera stores in the photo.
 */
@Service
public class AvatarService {

	/** Width and height of a stored picture: sharp up to about 64px circles on 2x screens, 40px on 3x. */
	public static final int SIZE = 128;
	/** Largest upload accepted. The app uploads about 5-10 KB; this leaves room, but not for raw camera photos. */
	public static final int MAX_UPLOAD_BYTES = 512 * 1024;
	/** Largest picture decoded (width x height), so a tiny file claiming huge dimensions cannot exhaust memory. */
	static final long MAX_SOURCE_PIXELS = 25_000_000L;
	static final float JPEG_QUALITY = 0.82f;
	private static final Set<String> FORMATS = Set.of("jpeg", "jpg", "png", "gif", "bmp");

	private final SecureRandom random = new SecureRandom();

	@Autowired
	private AvatarDao avatarDao;

	@Autowired
	private UserDAO userDAO;

	/** Stores a new profile picture for the user and returns its key (see User.avatarKey). */
	@Transactional
	public String store(Long userId, byte[] upload) {
		byte[] image = normalise(upload);
		User user = userDAO.getUserById(userId);
		if (user == null) {
			throw new BusinessLogicException("User not found", null, HttpStatus.NOT_FOUND);
		}
		avatarDao.save(userId, image, Instant.now());
		String key = newKey();
		user.setAvatarKey(key);
		userDAO.updateUser(user);
		return key;
	}

	/** Removes the user's profile picture, if any. */
	@Transactional
	public void remove(Long userId) {
		avatarDao.delete(userId);
		User user = userDAO.getUserById(userId);
		if (user != null && user.getAvatarKey() != null) {
			user.setAvatarKey(null);
			userDAO.updateUser(user);
		}
	}

	/** The picture's JPEG bytes, or null unless the key is the user's current one. */
	@Transactional
	public byte[] image(Long userId, String key) {
		if (userId == null || key == null) {
			return null;
		}
		User user = userDAO.getUserById(userId);
		if (user == null || !key.equals(user.getAvatarKey())) {
			return null;
		}
		UserAvatar avatar = avatarDao.get(userId);
		return avatar == null ? null : avatar.getImage();
	}

	/** 128 random bits, URL safe (22 characters). */
	private String newKey() {
		byte[] bytes = new byte[16];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Decodes an uploaded picture and returns it as a SIZE x SIZE JPEG, centre cropped to a square. */
	static byte[] normalise(byte[] upload) {
		if (upload == null || upload.length == 0) {
			throw invalid("The picture is empty.");
		}
		if (upload.length > MAX_UPLOAD_BYTES) {
			throw new BusinessLogicException("The picture is too large.", null, HttpStatus.PAYLOAD_TOO_LARGE);
		}
		BufferedImage source;
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
			Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
			if (readers == null || !readers.hasNext()) {
				throw invalid("This file is not a picture.");
			}
			ImageReader reader = readers.next();
			try {
				if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
					throw invalid("Please use a JPEG or PNG picture.");
				}
				reader.setInput(in, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width < 1 || height < 1 || (long) width * height > MAX_SOURCE_PIXELS) {
					throw invalid("The picture's dimensions are not supported.");
				}
				source = reader.read(0);
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof BusinessLogicException) {
				throw (BusinessLogicException) e;
			}
			throw invalid("This picture could not be read.");
		}
		return jpeg(scaleSquare(source, SIZE));
	}

	/** Centre crop to a square, then shrink in halving steps (smoother than one big step) to size x size. */
	static BufferedImage scaleSquare(BufferedImage source, int size) {
		int side = Math.min(source.getWidth(), source.getHeight());
		BufferedImage current = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2,
				side, side);
		int currentSize = side;
		while (currentSize / 2 >= size) {
			currentSize /= 2;
			current = draw(current, currentSize);
		}
		return draw(current, size);
	}

	/** Draws the image at size x size on white (JPEG has no transparency). */
	private static BufferedImage draw(BufferedImage image, int size) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, size, size);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.drawImage(image, 0, 0, size, size, null);
		} finally {
			g.dispose();
		}
		return out;
	}

	private static byte[] jpeg(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
		} catch (IOException e) {
			throw new BusinessLogicException("The picture could not be saved.", e, HttpStatus.INTERNAL_SERVER_ERROR);
		} finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	private static BusinessLogicException invalid(String message) {
		return new BusinessLogicException(message, null, HttpStatus.BAD_REQUEST);
	}
}
