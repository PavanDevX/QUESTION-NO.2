package com.example.bajaj;

import com.example.bajaj.model.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@SpringBootApplication
public class BajajApplication implements CommandLineRunner {

    private final RestTemplate restTemplate = new RestTemplate();

    public static void main(String[] args) {
        SpringApplication.run(BajajApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String url = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";

        UserInfo user = new UserInfo("John Doe", "REG12348", "john@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserInfo> entity = new HttpEntity<>(user, headers);

        try {
            ResponseEntity<WebhookResponse> response = restTemplate.postForEntity(url, entity, WebhookResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                WebhookResponse body = response.getBody();
                int findId = body.getData().getFindId();
                int n = body.getData().getN();

                List<Integer> outcome = findNthLevelFollowers(body.getData().getUsers(), findId, n);
                ResultQ2 result = new ResultQ2(user.getRegNo(), outcome);

                sendToWebhook(body.getWebhook(), body.getAccessToken(), result);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Integer> findNthLevelFollowers(List<User> users, int startId, int level) {
        Map<Integer, List<Integer>> graph = new HashMap<>();
        for (User user : users) {
            graph.put(user.getId(), user.getFollows());
        }

        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(startId);
        visited.add(startId);

        int currentLevel = 0;

        while (!queue.isEmpty() && currentLevel < level) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                int current = queue.poll();
                List<Integer> neighbors = graph.getOrDefault(current, Collections.emptyList());
                for (int neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        queue.offer(neighbor);
                        visited.add(neighbor);
                    }
                }
            }
            currentLevel++;
        }

        List<Integer> result = new ArrayList<>(queue);
        Collections.sort(result);
        return result;
    }

    private void sendToWebhook(String webhook, String token, ResultQ2 result) {
        int attempts = 4;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);

        HttpEntity<ResultQ2> request = new HttpEntity<>(result, headers);

        while (attempts-- > 0) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(webhook, request, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    System.out.println("Success: Data sent to webhook.");
                    return;
                }
            } catch (Exception e) {
                System.err.println("Webhook attempt failed. Retries left: " + attempts);
            }
        }

        System.err.println("All retries failed. Webhook delivery unsuccessful.");
    }
}
