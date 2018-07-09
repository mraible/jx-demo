package com.okta.developer.cli;

import com.okta.sdk.client.Client;
import com.okta.sdk.resource.PropertyRetriever;
import com.okta.sdk.resource.application.OpenIdConnectApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class AppRedirectUriManager implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AppRedirectUriManager.class);

    private final Client client;

    @Value("${appId}")
    private String appId;

    @Value("${redirectUri}")
    private String redirectUri;

    @Value("${operation:add}")
    private String operation;

    public AppRedirectUriManager(Client client) {
        this.client = client;
    }

    public static void main(String[] args) {
        SpringApplication.run(AppRedirectUriManager.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Adjusting Okta settings: {appId: {}, redirectUri: {}, operation: {}}", appId, redirectUri, operation);
        OpenIdConnectApplication app = (OpenIdConnectApplication) client.getApplication(appId);

        // update login redirect URIs
        List<String> redirectUris = app.getSettings().getOAuthClient().getRedirectUris();
        redirectUris = updateURIs(redirectUris);
        app.getSettings().getOAuthClient().setRedirectUris(redirectUris);

        // update logout redirect URI
        List<String> logoutUris = ((PropertyRetriever) app.getSettings().getOAuthClient())
                .getStringList("post_logout_redirect_uris");
        logoutUris = updateURIs(logoutUris);
        // todo: update app's logout URIs
        app.update();
        System.exit(0);
    }

    private List<String> updateURIs(List<String> redirectUris) {
        if (operation.equalsIgnoreCase("add")) {
            if (!redirectUris.contains(redirectUri)) {
                redirectUris.add(redirectUri);
            }
        } else if (operation.equalsIgnoreCase("remove")) {
            redirectUris.remove(redirectUri);
        }
        return redirectUris;
    }
}
