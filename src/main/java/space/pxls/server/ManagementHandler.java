package space.pxls.server;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Map;

public class ManagementHandler implements HttpHandler {
    private static final String SECRET_TOKEN = "changeme"; // TODO: Move to config
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        // Now safe to do blocking IO
        if (!exchange.getRequestMethod().equalToString("POST")) {
            exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.getResponseSender().send("Method Not Allowed");
            return;
        }

        String token = exchange.getRequestHeaders().getFirst("X-Manage-Token");
        if (token == null || !token.equals(SECRET_TOKEN)) {
            exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
            exchange.getResponseSender().send("Unauthorized");
            return;
        }

        exchange.startBlocking();
        InputStream is = exchange.getInputStream();
        Map<String, Object> body = objectMapper.readValue(is, Map.class);
        String command = (String) body.get("command");
        if (command == null) {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            exchange.getResponseSender().send("Missing command");
            return;
        }

        switch (command) {
            case "ping":
                exchange.setStatusCode(StatusCodes.OK);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exchange.getResponseSender().send("{\"result\":\"pong\"}");
                break;
            case "addroles": {
                String username = (String) body.get("username");
                Object rolesObj = body.get("roles");
                if (username == null || rolesObj == null || !(rolesObj instanceof java.util.List)) {
                    exchange.setStatusCode(StatusCodes.BAD_REQUEST);
                    exchange.getResponseSender().send("Missing username or roles");
                    return;
                }
                var user = space.pxls.App.getUserManager().getByName(username);
                if (user == null) {
                    exchange.setStatusCode(StatusCodes.NOT_FOUND);
                    exchange.getResponseSender().send("User not found");
                    return;
                }
                java.util.List<?> rolesList = (java.util.List<?>) rolesObj;
                java.util.List<space.pxls.user.Role> foundRoles = new java.util.ArrayList<>();
                java.util.List<String> notFound = new java.util.ArrayList<>();
                for (Object roleObj : rolesList) {
                    String roleStr = String.valueOf(roleObj);
                    var role = space.pxls.user.Role.fromID(roleStr);
                    if (role == null) {
                        var rolesByName = space.pxls.user.Role.fromNames(roleStr);
                        if (!rolesByName.isEmpty()) {
                            role = rolesByName.get(0);
                        }
                    }
                    if (role != null) {
                        foundRoles.add(role);
                    } else {
                        notFound.add(roleStr);
                    }
                }
                if (!foundRoles.isEmpty()) {
                    user.addRoles(foundRoles, false);
                }
                exchange.setStatusCode(StatusCodes.OK);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                StringBuilder sb = new StringBuilder();
                sb.append("{\"result\":\"roles added\"");
                sb.append(",\"added\":").append(space.pxls.server.ManagementHandler.objectMapper.writeValueAsString(foundRoles.stream().map(space.pxls.user.Role::getID).toArray()));
                sb.append(",\"not_found\":").append(space.pxls.server.ManagementHandler.objectMapper.writeValueAsString(notFound));
                sb.append("}");
                exchange.getResponseSender().send(sb.toString());
                break;
            }
            case "users": {
                var authedUsers = space.pxls.App.getServer().getAuthedUsers().values();
                java.util.List<String> usernames = new java.util.ArrayList<>();
                for (var user : authedUsers) {
                    usernames.add(user.getName());
                }
                exchange.setStatusCode(StatusCodes.OK);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exchange.getResponseSender().send(objectMapper.writeValueAsString(usernames));
                break;
            }
            // TODO: Add more commands (reloadConfig, shutdown, etc.)
            default:
                exchange.setStatusCode(StatusCodes.BAD_REQUEST);
                exchange.getResponseSender().send("Unknown command");
        }
    }
} 