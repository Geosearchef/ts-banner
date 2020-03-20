package de.geosearchef;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static spark.Spark.get;
import static spark.Spark.port;

public class TSBanner {

	public static final int PORT = 23020;
	public static final Duration REQUEST_COUNT_CLEAR_INTERVAL = Duration.ofMinutes(5);
	public static final int WIDTH = 1500;
	public static final int HEIGHT = 500;
	public static final Duration DISPLAY_TIME_BACKGROUND_SELECTION = Duration.ofMinutes(5 * 999999);
	public static final Path BACKGROUND_DIR = Paths.get("./backgrounds");

	private static Map<String, Integer> requestCount = new HashMap<>();
	private static Map<String, Instant> previousRequests = new HashMap<>(); // rate limiting
	private static Map<String, Instant> loginTimes = new HashMap<>(); // rate limiting

	private static Map<String, BufferedImage> backgrounds = new HashMap<>();
	private static Font font;
	private static Locale locale = new Locale("de", "DE");
	private static Color fontColor = new Color(33, 33, 33);
	private static String settingsHtml;

	static {
		try {
			font = Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(Paths.get("./CinzelDecorative-Regular.ttf").toFile()));

			Files.list(BACKGROUND_DIR).forEach(p -> {
				try {
					backgrounds.put(p.getFileName().toString(), ImageIO.read(p.toFile()));
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

			settingsHtml = new String(Files.readAllBytes(Paths.get("./settings.html")));
			settingsHtml = settingsHtml.replace("%SELECT_OPTIONS%",
					backgrounds.keySet().stream()
							.map(b -> String.format("<option value=\"%s\">%s</option>", b, b))
							.collect(Collectors.joining("\n"))
			);
		} catch (IOException | FontFormatException e) {
			e.printStackTrace();
		}
	}

	public static BufferedImage generateImage(Instant loginTime, Settings.UserSettings userSettings) throws IOException {
		var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

		var g = (Graphics2D) image.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		if(userSettings.getBackgroundName() == null) {
			g.setColor(new Color(143, 143, 143));
			g.fillRect(0, 0, 2000, 2000);
		} else {
			g.drawImage(backgrounds.get(userSettings.getBackgroundName()), 0, 0, null);
		}

		if(userSettings.getBackgroundName() == null && loginTime.plus(DISPLAY_TIME_BACKGROUND_SELECTION).isAfter(Instant.now())) {
			renderCentered(g,
					"Click here to select a background", WIDTH / 2.0f, HEIGHT * 0.05f,
					font.deriveFont(20.0f), new Color(255, 255, 255)
			);
		}


		int highlightWidth = (int)(WIDTH * 0.7f);
		int highlightHeight = (int)(HEIGHT * 0.6f);
		int highlightX = (WIDTH - highlightWidth) / 2;
		int highlightY = (HEIGHT - highlightHeight) / 2;
		g.setPaint(new GradientPaint(
				highlightX, HEIGHT / 2, new Color(1f, 1f, 1f, 0f),
				highlightX + highlightWidth / 2, HEIGHT / 2, new Color(1f, 1f, 1f, 0.7f),
				true));
		g.fill(new Rectangle(highlightX, highlightY, highlightWidth, highlightHeight));


		String greeting = "Welcome to OwOty";
		Calendar calendar = Calendar.getInstance(locale);
		int time = calendar.get(Calendar.HOUR_OF_DAY) * 100 + calendar.get(Calendar.MINUTE);
//		time = 21_00;
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
		g.fillRect((int)(WIDTH * 0.316f), (int)(HEIGHT * 0.9f), (int)(WIDTH * 0.368f), (int)(HEIGHT * 0.1f));
		renderCentered(g,
				"Work in progress - suggestions appreciated", WIDTH / 2.0f, HEIGHT * 0.95f,
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
		Settings.init();

		port(PORT);

		get("/banner", (req, res) -> {
			try {
				System.out.println("Request from " + req.ip());
				Instant lastReq = previousRequests.get(req.ip());
				int totalAttempts = 0;
				synchronized (requestCount) {
					totalAttempts = Optional.ofNullable(requestCount.get(req.ip())).orElse(0);
					requestCount.put(req.ip(), totalAttempts + 1);
				}
				if(totalAttempts > 50) {
					System.out.println("denied request due to spam prevention");
					res.status(429); // 429 TOO MANY REQUESTS
					return "Settings saved.<br>Couldn't preview banner. Your request looks too spammy. Try again later.";
				}

				if(lastReq == null || lastReq.plus(Duration.ofMinutes(10)).isBefore(Instant.now())) {
					loginTimes.put(req.ip(), Instant.now());
				}

				previousRequests.put(req.ip(), Instant.now());

				res.raw().setContentType("image/png");

				BufferedImage image = generateImage(loginTimes.get(req.ip()), Settings.getUserSettings(req.ip()));
				try (var out = res.raw().getOutputStream()) {
					ImageIO.write(image, "png", out);
					out.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}

				return res.raw();
			} catch(Exception e) {
				e.printStackTrace();
				return "Encountered internal error while processing requested.";
			}
		});

		get("/settings", (req, res) -> {
			res.raw().setContentType("text/html");
			try (var out = new DataOutputStream(res.raw().getOutputStream())) {
				out.write(settingsHtml.getBytes());
				out.flush();
			}
			return res.raw();
		});

		get("/submit_settings", (req, res) -> {
			if(! req.queryParams().contains("background") || !backgrounds.containsKey(req.queryParams("background"))) {
				res.status(400);//bad request
				return "Bad request";
			}
			var background = req.queryParams("background");

			Settings.setUserSettings(new Settings.UserSettings(req.ip(), background));

			res.redirect("/banner");
			return res;
		});

		startRequestCountClearer();
	}

	private static void startRequestCountClearer() {
		new Thread(() -> {
			while(true) {
				synchronized (requestCount) {
					requestCount.clear();
				}
				try { Thread.sleep(REQUEST_COUNT_CLEAR_INTERVAL.toMillis()); } catch(InterruptedException e) {e.printStackTrace();}
			}
		}).start();
	}
}
