package util;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import jackson.AllCardsJson;
import jackson.AllSetsJson;
import jackson.JacksonUtil;

public class WebUtil {

	public static final String URL_MTG_JSON = "https://www.mtgjson.com";
	public static final String URL_MTG_CHANGE_LOG = URL_MTG_JSON + "/json/version.json";
	public static final String URL_MTG_JSON_SETS = URL_MTG_JSON + "/json/AllPrintings.json.zip";
	public static final String URL_MTG_JSON_CARDS = URL_MTG_JSON + "/json/AllCards.json.zip";

	private static final String SCRAPE_START_VERSION = "\"version\": \"";
	private static final String SCRAPE_END_VERSION = "\"";

	public static void init() {
		downloadNewestJSONS();
	}

	private WebUtil() {}

	public static void browse(String url) {
		try {
			browse(new URL(url));
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public static void browse(URL url) {
		try {
			Desktop.getDesktop().browse(url.toURI());
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public static File download(String url, File outputFile) {
		return download(url, outputFile == null ? null : outputFile.getAbsolutePath());
	}

	public static File downloadOld(String url, String outputFilename) {
		URL website = null;
		try {
			website = new URL(url);
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		}

		if(website == null) {
			return null;
		}

		try (InputStream inputStream = website.openStream();
			 ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
			 FileOutputStream fileOutputStream = new FileOutputStream(outputFilename)) {
			fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, 1 << 24);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		File file = new File(outputFilename);
		if(file.exists()) {
			return file;
		}
		return null;
	}

	public static File download(String url, String outputFilename) {
		try {
			new FileOutputStream(outputFilename).getChannel().transferFrom(Channels.newChannel(new URL(url).openStream()), 0, Long.MAX_VALUE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		File file = new File(outputFilename);
		if(file.exists()) {
			return file;
		}
		return null;
	}

	public static StringBuilder readHtml(String url) {
		URL website = null;
		try {
			website = new URL(url);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		StringBuilder sb = new StringBuilder();
        try(InputStream in = website.openStream();
        	InputStreamReader isr = new InputStreamReader(in);
        	BufferedReader br = new BufferedReader(isr)) {
        	String line;
        	while ((line = br.readLine()) != null) {
        		sb.append(line);
        		sb.append(System.lineSeparator());
            }
		} catch (IOException e) {
			e.printStackTrace();
		}

        return sb;
	}

	public static String scrapeUrl(String url, String start, String end) {
		return scrape(readHtml(url).toString(), start, end);
	}

	public static String scrape(String html, String start, String end) {
		String regexString = Pattern.quote(start) + "(.*?)" + Pattern.quote(end);
		Pattern pattern = Pattern.compile(regexString);
		Matcher matcher = pattern.matcher(html);
		while(matcher.find()) {
			String text = matcher.group(1);
			if(text != null) {
				return text;
			}
		}
		return null;
	}

	private static void deriveJsonFiles() throws Exception {
		System.out.println("Creating \"card\" and \"set\" master files.");
		AllCardsJson cards = JacksonUtil.readExternal(AllCardsJson.class, FileUtil.RESOURCE_CARDS_JSONS);
		AllSetsJson sets = JacksonUtil.readExternal(AllSetsJson.class, FileUtil.RESOURCE_SETS_JSONS);
		cards.init(sets);
		JacksonUtil.writeExternal(cards, FileUtil.RESOURCE_CARDS_LESS_JSONS);
		JacksonUtil.writeExternal(sets, FileUtil.RESOURCE_SETS_LESS_JSONS);
	}

	private static void cleanupJsonFiles() {
		List<File> files = new ArrayList<File>(6);
		files.add(FileUtil.getExternalResourcesFile(FileUtil.RESOURCE_SETS_JSONS));
		files.add(FileUtil.getExternalResourcesFile(FileUtil.RESOURCE_CARDS_JSONS));
		files.add(FileUtil.getExternalResourcesFile(FileUtil.RESOURCE_SETS_JSONS + ".zip"));
		files.add(FileUtil.getExternalResourcesFile(FileUtil.RESOURCE_CARDS_JSONS + ".zip"));

		for(File file : files) {
			if(file.exists()) {
				file.delete();
			}
		}
	}

	public static int downloadNewestJSONS() {
		System.setProperty("http.agent", "Chrome");
		int error = 0;

		String jandorVersion = VersionUtil.readVersionFromFile();
		String mtgJsonVersion = scrapeUrl(URL_MTG_CHANGE_LOG, SCRAPE_START_VERSION, SCRAPE_END_VERSION);

		if(jandorVersion == null || mtgJsonVersion == null) {
			if(jandorVersion == null) {
				System.err.println("Could not determine mtgjson.com version being used by Jandor");
				error++;
			}
			if(mtgJsonVersion == null) {
				System.err.println("Could not determine current version used by mtgjson.com");
				error++;
			}
			return error;
		} else if(jandorVersion.equals(mtgJsonVersion)) {
			if(!FileUtil.getExternalResourcesFile(FileUtil.RESOURCE_CARDS_LESS_JSONS).exists() ||
				!FileUtil.getExternalResourcesFile(FileUtil.RESOURCE_SETS_LESS_JSONS).exists()) {
				try {
					deriveJsonFiles();
				} catch (Exception e) {
					System.err.println("Could not create master files.");
					error++;
					return error;
				}
			}
			cleanupJsonFiles();
			System.out.println("Jandor is up-to-date with mtgjson.com version " + mtgJsonVersion);
			return error;
		}

		System.out.println("Updating Jandor from mtgjson.com version " + jandorVersion + " to " + mtgJsonVersion);

		File setJsons = download(URL_MTG_JSON_SETS, FileUtil.getExternalResourcesFile(FileUtil.RESOURCE_SETS_JSONS + ".zip"));
		File cardJsons = download(URL_MTG_JSON_CARDS, FileUtil.getExternalResourcesFile(FileUtil.RESOURCE_CARDS_JSONS + ".zip"));

		if(setJsons == null) {
			error++;
			System.err.println("Downloading " + URL_MTG_JSON_SETS + " failed.");
		} else {
			ZipUtil.unzip(setJsons, FileUtil.getExternalResourcesFolder());
			//setJsons.delete();
		}

		if(cardJsons == null) {
			error++;
			System.err.println("Downloading " + URL_MTG_JSON_CARDS + " failed.");
		} else {
			ZipUtil.unzip(cardJsons, FileUtil.getExternalResourcesFolder());
			//cardJsons.delete();
		}

		if(error == 0) {
			try {
				deriveJsonFiles();
			} catch (Exception e) {
				System.err.println("Could not create master files.");
				error++;
			}
		}

		cleanupJsonFiles();

		if(error == 0) {
			FileUtil.writeString(mtgJsonVersion, FileUtil.getExternalResourcesFolder() + File.separator + FileUtil.RESOURCE_MTG_JSON_VERSION, false);
		}

		return error;
	}

	public static interface OnSuccess {

		public void onSuccess(String response);

	}

	public static interface OnError {

		public void onError(String error);

	}

	public static void doPost(String urlStr, Object postDataJson, OnSuccess onSuccess, OnError onError) {
		try {
			doPost(new URL(urlStr), postDataJson, onSuccess, onError);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void doPost(URL url, Object postDataJson, OnSuccess onSuccess, OnError onError) {
		TaskUtil.run(() -> {
			doPostInner(url, postDataJson, onSuccess, onError);
		});
	}

	private static void doPostInner(URL url, Object postDataJson, OnSuccess onSuccess, OnError onError) {
		String postDataStr = JacksonUtil.toString(postDataJson);
	    System.out.println("POST url: " + url);
	    System.out.println("POST params: " + postDataStr);
	    HttpsURLConnection postConnection;
		try {
			postConnection = (HttpsURLConnection) url.openConnection();
			postConnection.setRequestMethod("POST");
		    postConnection.setRequestProperty("Content-Type", "application/json");
		    postConnection.setDoOutput(true);
		    OutputStream os = postConnection.getOutputStream();
		    os.write(postDataStr.getBytes());
		    os.flush();
		    os.close();

		    int responseCode = postConnection.getResponseCode();
		    System.out.println("POST Response Code :  " + responseCode);
		    System.out.println("POST Response Message : " + postConnection.getResponseMessage());
		    if (responseCode == HttpsURLConnection.HTTP_OK) { //success
		        BufferedReader in = new BufferedReader(new InputStreamReader(postConnection.getInputStream()));
		        String inputLine;
		        StringBuffer response = new StringBuffer();
		        while ((inputLine = in .readLine()) != null) {
		            response.append(inputLine);
		        }
		        in .close();

		        if(onSuccess != null) {
		        	onSuccess.onSuccess(response.toString());
		        }
		    } else {
		    	if(onError != null) {
		    		onError.onError("POST did not work!");
		    	}
		    }
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static <T> void doMultiPost(String urlStr, List<T> postDataJsons, OnSuccess onSuccess, OnError onError) {
		try {
			doMultiPost(new URL(urlStr), postDataJsons, onSuccess, onError);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public static <T> void doMultiPost(URL url, List<T> postDataJsons, OnSuccess onSuccess, OnError onError) {
		TaskUtil.run(() -> {
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			for(Object postDataJson : postDataJsons) {
				doPostInner(url, postDataJson, (String responseStr) -> {
					if(sb.length() > 1) {
						sb.append(",");
					}
					sb.append(responseStr);
				}, null);
			}
			sb.append("]");
			if(onSuccess != null) {
				onSuccess.onSuccess(sb.toString());
			}
		});
	}

}
