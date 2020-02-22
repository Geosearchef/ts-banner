package de.geosearchef;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.nonNull;
import static spark.Spark.get;
import static spark.Spark.port;

public class TSBanner {

	public static final int PORT = 1338;
	public static final Duration REQUEST_INTERVAL = Duration.ofSeconds(3);
	public static final int WIDTH = 800;
	public static final int HEIGHT = 200;

	private static Map<String, Instant> previousRequests = new HashMap<>(); // rate limiting

	public static BufferedImage generateImage() {
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = (Graphics2D) image.getGraphics();

		g.setColor(new Color(196, 90, 0));
		g.fillRect(0, 0, WIDTH, HEIGHT);

		return image;
	}

	public static void main(String args[]) {
		port(PORT);

		get("/banner", (req, res) -> {
			Instant lastReq = previousRequests.get(req.ip());
			if(nonNull(lastReq) && lastReq.plus(REQUEST_INTERVAL).isAfter(Instant.now())) {
				System.out.println("denied request due to spam prevention");
				res.status(429); // 429 TOO MANY REQUESTS
				return res;
			}
			previousRequests.put(req.ip(), Instant.now());

			res.raw().setContentType("image/png");

			BufferedImage image = generateImage();
			try (OutputStream out = res.raw().getOutputStream()) {
				ImageIO.write(image, "png", out);
				out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return res.raw();
		});
	}
}
