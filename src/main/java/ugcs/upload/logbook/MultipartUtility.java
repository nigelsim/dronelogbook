package ugcs.upload.logbook;

import lombok.SneakyThrows;
import ugcs.common.security.MD5HashCalculator;
import ugcs.exceptions.ExpectedException;
import ugcs.exceptions.logbook.LogBookAuthorizationFailed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.util.Collections.emptyList;

/**
 * Builder for multipart data http-request
 */
public class MultipartUtility {
    private final String boundary;
    private static final String LINE_FEED = "\r\n";
    private HttpURLConnection httpConn;
    private String charset;
    private OutputStream outputStream;
    private PrintWriter writer;
    private boolean isAuthorisationTest = false;

    @SneakyThrows
    public MultipartUtility(String requestURL, String charset) {
        this.charset = charset;

        boundary = "===" + System.currentTimeMillis() + "===";

        URL url = new URL(requestURL);
        httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true);
        httpConn.setDoInput(true);
        httpConn.setRequestMethod("POST");
        httpConn.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary);
        httpConn.setRequestProperty("User-Agent", "CodeJava Agent");
        httpConn.setRequestProperty("DEBUG", "UGCS");
        outputStream = httpConn.getOutputStream();
        writer = new PrintWriter(new OutputStreamWriter(outputStream, charset),
                true);
    }

    public MultipartUtility(String requestUrl) {
        this(requestUrl, "UTF-8");
    }

    public void addFormField(String name, String value) {
        writer.append("--").append(boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(LINE_FEED);
        writer.append("Content-Type: text/plain; charset=").append(charset).append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.append(value).append(LINE_FEED);
        writer.flush();
    }

    @SneakyThrows
    public void addFilePart(String fieldName, File uploadFile) {
        String fileName = uploadFile.getName();
        writer.append("--").append(boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"")
                .append(fileName).append("\"").append(LINE_FEED);
        writer.append("Content-Type: ").append(URLConnection.guessContentTypeFromName(fileName)).append(LINE_FEED);
        writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.flush();

        FileInputStream inputStream = new FileInputStream(uploadFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        inputStream.close();

        writer.append(LINE_FEED);
        writer.flush();
    }

    public MultipartUtility withCredentials(String login, String rawPasswordOrMd5Hash) {
        addFormField("login", login);
        addFormField("password", MD5HashCalculator.of(rawPasswordOrMd5Hash).hash());

        return this;
    }

    public MultipartUtility authorisationTestOnly() {
        writer.append("--").append(boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"").append("file").append("\"; filename=\"")
                .append("login_try").append("\"").append(LINE_FEED);
        writer.append("Content-Type: ").append("UTF-8").append(LINE_FEED);
        writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.flush();

        writer.append(LINE_FEED);
        writer.flush();

        isAuthorisationTest = true;

        return this;
    }

    public void addHeaderField(String name, String value) {
        writer.append(name).append(": ").append(value).append(LINE_FEED);
        writer.flush();
    }

    public List<String> finish() {
        try {
            List<String> response = new ArrayList<>();

            writer.append(LINE_FEED).flush();
            writer.append("--").append(boundary).append("--").append(LINE_FEED);
            writer.close();

            int status = httpConn.getResponseCode();
            System.out.println(String.valueOf(status));
            switch (status) {
                case HTTP_OK:
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            httpConn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.add(line);
                    }
                    reader.close();
                    httpConn.disconnect();
                    break;
                case HTTP_UNAUTHORIZED:
                    throw new LogBookAuthorizationFailed();
                case HTTP_INTERNAL_ERROR:
                    if (isAuthorisationTest) {
                        return emptyList();
                    }
                default:
                    throw new ExpectedException("Uploading data to LogBook failed.");
            }
            return response;
        } catch (IOException connectException) {
            throw new ExpectedException("LogBook service unavailable.", connectException);
        } finally {
            writer.close();
        }
    }
}