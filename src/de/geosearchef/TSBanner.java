package de.geosearchef;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.util.Objects.nonNull;
import static spark.Spark.get;
import static spark.Spark.port;

public class TSBanner {

	public static final int PORT = 1338;
	public static final Duration REQUEST_INTERVAL = Duration.ofSeconds(1);//TODO
	public static final int WIDTH = 1500;
	public static final int HEIGHT = 500;

	private static Map<String, Instant> previousRequests = new HashMap<>(); // rate limiting
	private static Map<String, Instant> loginTimes = new HashMap<>(); // rate limiting

	private static BufferedImage background;
	private static Font font;
	private static Locale locale = new Locale("de", "DE");
	private static Color fontColor = new Color(33, 33, 33);

	static {
		try {
			background = ImageIO.read(Paths.get("./background.png").toFile());
			font = Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(Paths.get("./CinzelDecorative-Regular.ttf").toFile()));
		} catch (IOException | FontFormatException e) {
			e.printStackTrace();
		}
	}

	public static BufferedImage generateImage(Instant loginTime) throws IOException {
		var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

		var g = (Graphics2D) image.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

//		g.drawImage(background, 0, 0, null);
		g.setColor(new Color(143, 143, 143));
		g.fillRect(0, 0, 2000, 2000);

		int highlightWidth = (int)(WIDTH * 0.7f);
		int highlightHeight = (int)(HEIGHT * 0.6f);
		int highlightX = (WIDTH - highlightWidth) / 2;
		int highlightY = (HEIGHT - highlightHeight) / 2;
		g.setPaint(new GradientPaint(
				highlightX, HEIGHT / 2, new Color(1f, 1f, 1f, 0f),
				highlightX + highlightWidth / 2, HEIGHT / 2, new Color(1f, 1f, 1f, 0.7f),
				true));
		g.fill(new Rectangle(highlightX, highlightY, highlightWidth, highlightHeight));


		String greeting = "Welcome to Iritiy";
		Calendar calendar = Calendar.getInstance(locale);
		int time = calendar.get(Calendar.HOUR_OF_DAY) * 100 + calendar.get(Calendar.MINUTE);
		if(time >= 23_30 || time < 1_00) {
			greeting = "Have a good night";
		} else if(time >= 22_00) {
			greeting = "Enjoy your stay";
		} else if(time < 2_00) {
			greeting = "You still here?";
		} else if(time < 6_00) {
			greeting = "zzzzz...";
		} else if(time < 11_00) {
			greeting = "Good morning";
		}

		renderCentered(g,
				greeting, WIDTH / 2.0f, HEIGHT * 0.29f,
				font.deriveFont(40.0f),
				fontColor
		);

		String timeString = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date.from(Instant.now()));
		renderCentered(g,
				timeString, WIDTH / 2.0f, HEIGHT * 0.43f,
				font.deriveFont(74.0f),
				fontColor
		);

		String dateString = DateFormat.getDateInstance(DateFormat.DEFAULT, locale).format(Date.from(Instant.now()));
		renderCentered(g,
				dateString, WIDTH / 2.0f, HEIGHT * 0.538f,
				font.deriveFont(24.0f),
				fontColor
		);



		renderCentered(g,
				"Online time:", WIDTH / 2.0f, HEIGHT * 0.66f,
				font.deriveFont(26.0f),
				fontColor
		);

		long minutesOnline = Duration.between(loginTime, Instant.now()).toMinutes();
		String onlineTimeString = String.format("%02d:%02d", (int)Math.floor(minutesOnline / 60), minutesOnline % 60);
		renderCentered(g,
				onlineTimeString, WIDTH / 2.0f, HEIGHT * 0.728f,
				font.deriveFont(32.0f),
				fontColor
		);


		g.setColor(new Color(1f, 1f, 1f, 0.5f));
		g.fillRect((int)(WIDTH * 0.204f), (int)(HEIGHT * 0.9f), (int)(WIDTH * 0.592f), (int)(HEIGHT * 0.1f));
		renderCentered(g,
				"Work in progress (background is placeholder), suggestions appreciated", WIDTH / 2.0f, HEIGHT * 0.95f,
				font.deriveFont(20.0f),
				fontColor
		);

		// TODO: temps?

		return image;
	}

	private static void renderCentered(Graphics2D g2d, String string, float x, float y, Font font, Color color) {
		var g = (Graphics2D) g2d.create();
		g.setFont(font);
		g.setColor(color);
		var fontMetrics = g.getFontMetrics();
		g.drawString(string, x - fontMetrics.stringWidth(string) / 2.0f, y + fontMetrics.getAscent() - fontMetrics.getHeight() / 2.0f);
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

			if(lastReq == null || lastReq.plus(Duration.ofMinutes(10)).isBefore(Instant.now())) {
				loginTimes.put(req.ip(), Instant.now());
			}

			previousRequests.put(req.ip(), Instant.now());

			res.raw().setContentType("image/png");

			BufferedImage image = generateImage(loginTimes.get(req.ip()));
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
