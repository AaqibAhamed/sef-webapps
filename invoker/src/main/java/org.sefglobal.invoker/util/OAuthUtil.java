package org.sefglobal.invoker.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.sefglobal.invoker.dto.AuthData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.sefglobal.invoker.exception.HTTPClientCreationException;
import org.sefglobal.invoker.exception.UnexpectedResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class OAuthUtil {
    private static Logger logger = LoggerFactory.getLogger(OAuthUtil.class);
    private static Environment environment;

    @Autowired
    private Environment env;

    @PostConstruct
    public void init(){
        OAuthUtil.environment = env;
    }

    public static AuthData generateToken(String username, String password, String scopes)
            throws IOException, ParseException,
            HTTPClientCreationException, UnexpectedResponseException {

        final String clientId = environment.getProperty("config.clientId");
        final String clientSecret = environment.getProperty("config.clientSecret");
        final String unEncodedClientCredentials = clientId+":"+clientSecret;

        String clientCredentials = Base64.getEncoder().encodeToString(unEncodedClientCredentials.getBytes());

        HttpPost tokenEndpoint = new HttpPost(environment.getProperty("config.tokenEndpoint"));
        tokenEndpoint.setHeader("Authorization",
                "Basic " + clientCredentials);
        tokenEndpoint.setHeader("Content-Type", ContentType.APPLICATION_FORM_URLENCODED.toString());

        StringEntity tokenEPPayload = new StringEntity(
                "grant_type=password&username=" + username + "&password=" + password + "&scope=" + scopes,
                ContentType.APPLICATION_FORM_URLENCODED);

        tokenEndpoint.setEntity(tokenEPPayload);
        String tokenResult = ControllerUtility.executePost(tokenEndpoint);
        JSONParser jsonParser = new JSONParser();
        JSONObject jTokenResult = (JSONObject) jsonParser.parse(tokenResult);
        String refreshToken = jTokenResult.get("refresh_token").toString();
        String accessToken = jTokenResult.get("access_token").toString();
        AuthData authData = new AuthData();
        authData.setAccessToken(accessToken);
        authData.setRefreshToken(refreshToken);
        authData.setClientCredentials(clientCredentials);
        authData.setUsername(username);
        logger.debug("Access Token retrieved with scopes: " + jTokenResult.get("scope").toString());
        return authData;
    }


    static void refreshToken(AuthData authData) throws IOException, HTTPClientCreationException {
        logger.debug("refreshing the token");
        HttpPost tokenEndpoint = new HttpPost(environment.getProperty("config.tokenEndpoint"));
        StringEntity tokenEndpointPayload = new StringEntity(
                "grant_type=refresh_token&refresh_token=" + authData.getRefreshToken()
                        + "&scope=PRODUCTION",
                ContentType.APPLICATION_FORM_URLENCODED);

        tokenEndpoint.setEntity(tokenEndpointPayload);
        tokenEndpoint.setHeader("Authorization", "Basic " + authData.getClientCredentials());
        tokenEndpoint.setHeader("Content-Type", ContentType.APPLICATION_FORM_URLENCODED.toString());

        CloseableHttpClient client = ControllerUtility.getHTTPClient();
        HttpResponse response = client.execute(tokenEndpoint);
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));
        StringBuilder resultBuffer = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            resultBuffer.append(line);
        }
        String tokenResult = resultBuffer.toString();
        if (response.getStatusLine().getStatusCode() == 200) {
            try {
                JSONParser jsonParser = new JSONParser();
                JSONObject jTokenResult = (JSONObject) jsonParser.parse(tokenResult);
                String refreshToken = jTokenResult.get("refresh_token").toString();
                String accessToken = jTokenResult.get("access_token").toString();
                authData.setAccessToken(accessToken);
                authData.setRefreshToken(refreshToken);
            } catch (ParseException e) {
                logger.error("Error while parsing refresh token response", e);
            }
        } else {
            logger.error("Error while parsing refresh token response, Token EP response : " +
                    response.getStatusLine().getStatusCode());
        }
        rd.close();
    }
}
