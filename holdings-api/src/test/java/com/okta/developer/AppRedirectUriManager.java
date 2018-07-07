package com.okta.developer;

import com.okta.sdk.client.Client;
import com.okta.sdk.resource.application.OpenIdConnectApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class AppRedirectUriManager implements ApplicationRunner {
    private final Client client;

    public AppRedirectUriManager(Client client) {
        this.client = client;
    }

    public static void main(String[] args) {
        SpringApplication.run(AppRedirectUriManager.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("appId") && !args.containsOption("redirectUri")) {
            System.err.println("You must specify an appId and redirectUri!");
            System.exit(1);
        } else {
            String appId = args.getOptionValues("appId").get(0);
            String redirectUri = args.getOptionValues("redirectUri").get(0);
            List<String> operationArgs = args.getOptionValues("operation");
            String operation = "add";
            if (operationArgs != null && operationArgs.size() > 0) {
                operation = operationArgs.get(0);
            }

            OpenIdConnectApplication app = (OpenIdConnectApplication) client.getApplication(appId);
            System.out.println(app);
            List<String> redirectUris = app.getSettings().getOAuthClient().getRedirectUris();

            if (operation.equalsIgnoreCase("add")) {
                if (!redirectUris.contains(redirectUri)) {
                    redirectUris.add(redirectUri);
                }
            } else if (operation.equalsIgnoreCase("remove")) {
                redirectUris.remove(redirectUri);
            }

            app.getSettings().getOAuthClient().setRedirectUris(redirectUris);
            app.update();
            //System.out.println("Updated app redirectURIs: " + redirectUris);
            System.exit(0);
        }
    }
}
