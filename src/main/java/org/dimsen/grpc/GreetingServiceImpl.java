package org.dimsen.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Singleton
@GrpcService
public class GreetingServiceImpl implements GreetingService {
    private static final Logger logger = Logger.getLogger(GreetingServiceImpl.class.getName());

    @Override
    public Uni<HelloReply> sayHello(HelloRequest request) {
        logger.info("Received sayHello request for name: " + request.getName());
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            logger.warning("Invalid request: name is empty");
            return Uni.createFrom().failure(
                Status.INVALID_ARGUMENT
                    .withDescription("Name cannot be empty")
                    .asRuntimeException()
            );
        }

        String message = "Hello " + request.getName() + "!";
        logger.info("Sending response: " + message);
        return Uni.createFrom().item(() -> HelloReply.newBuilder()
                .setMessage(message)
                .build());
    }

    @Override
    public Uni<HelloReply> sayHelloWithMood(HelloRequestWithMood request) {
        logger.info("Received sayHelloWithMood request for name: " + request.getName() + " with mood: " + request.getMood());
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            logger.warning("Invalid request: name is empty");
            return Uni.createFrom().failure(
                Status.INVALID_ARGUMENT
                    .withDescription("Name cannot be empty")
                    .asRuntimeException()
            );
        }

        if (request.getMood() == null) {
            logger.warning("Invalid request: mood is not specified");
            return Uni.createFrom().failure(
                Status.INVALID_ARGUMENT
                    .withDescription("Mood must be specified")
                    .asRuntimeException()
            );
        }

        String message = switch (request.getMood()) {
            case HAPPY -> "Hello " + request.getName() + "! 😊";
            case SAD -> "Hello " + request.getName() + "... 😢";
            case EXCITED -> "Hello " + request.getName() + "! 🎉";
            case ANGRY -> "Hello " + request.getName() + "! 😠";
            default -> "Hello " + request.getName() + "!";
        };

        logger.info("Sending response: " + message);
        return Uni.createFrom().item(() -> HelloReply.newBuilder()
                .setMessage(message)
                .build());
    }

    @Override
    public Multi<HelloReply> sayHelloStream(HelloRequest request) {
        logger.info("Starting sayHelloStream for name: " + request.getName());
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            logger.warning("Invalid request: name is empty");
            return Multi.createFrom().failure(
                Status.INVALID_ARGUMENT
                    .withDescription("Name cannot be empty")
                    .asRuntimeException()
            );
        }

        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(tick -> {
                    String message = "Hello " + request.getName() + "! (Message #" + (tick + 1) + ")";
                    logger.info("Streaming message: " + message);
                    return HelloReply.newBuilder()
                            .setMessage(message)
                            .build();
                })
                .select().first(5)
                .onCompletion().invoke(() -> logger.info("Completed streaming for: " + request.getName()));
    }

    @Override
    public Uni<HelloReply> collectNames(Multi<HelloRequest> requests) {
        logger.info("Starting collectNames stream");
        List<String> names = new ArrayList<>();

        return requests
                .onItem().invoke(request -> {
                    if (request.getName() != null && !request.getName().trim().isEmpty()) {
                        names.add(request.getName());
                        logger.info("Collected name: " + request.getName());
                    } else {
                        logger.warning("Received empty name in stream");
                    }
                })
                .collect().last()
                .onItem().transform(lastRequest -> {
                    if (names.isEmpty()) {
                        logger.warning("No valid names collected");
                        throw Status.INVALID_ARGUMENT
                                .withDescription("No valid names provided")
                                .asRuntimeException();
                    }
                    String message = "Hello to: " + String.join(", ", names) + "!";
                    logger.info("Sending collected names response: " + message);
                    return HelloReply.newBuilder()
                            .setMessage(message)
                            .build();
                });
    }

    @Override
    public Multi<ChatMessage> chat(Multi<ChatMessage> requests) {
        logger.info("Starting chat session");
        
        return requests
                .onItem().invoke(message -> 
                    logger.info("Received chat message from " + message.getSender() + ": " + message.getContent())
                )
                .onItem().transform(message -> {
                    // Echo the message back with a timestamp
                    ChatMessage response = ChatMessage.newBuilder()
                            .setSender("Server")
                            .setContent("Echo: " + message.getContent())
                            .setTimestamp(Instant.now().toEpochMilli())
                            .build();
                    logger.info("Sending chat response: " + response.getContent());
                    return response;
                })
                .onCompletion().invoke(() -> logger.info("Chat session ended"));
    }
} 