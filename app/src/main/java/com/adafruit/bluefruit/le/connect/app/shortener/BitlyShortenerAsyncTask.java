package com.adafruit.bluefruit.le.connect.app.shortener;


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class BitlyShortenerAsyncTask extends ShortenerAsyncTask {

    // Constants
    private static final String kBitlyApiKey = "38bc9301550f6eeec36db33334701e3a551f580d";


    public BitlyShortenerAsyncTask(ShortenerListener listener) {
       super(listener);
    }

    @Override
    protected String doInBackground(String... urls) {
        try {
            String originalUrl = urls[0];
            String url = bitlyShorteningEndPoint(originalUrl);

            HttpGet httpGet = new HttpGet(url);
            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response = httpclient.execute(httpGet);

            final int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                HttpEntity entity = response.getEntity();
                String data = EntityUtils.toString(entity);
                return data;
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String bitlyShorteningEndPoint(String uri) throws UnsupportedEncodingException {
        String encodedUri = URLEncoder.encode(uri, "UTF-8");
        return String.format("https://api-ssl.bitly.com/v3/shorten?access_token=%s&longUrl=%s&format=txt", kBitlyApiKey, encodedUri);
    }
}
