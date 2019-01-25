package com.example.fu;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.r2dbc.function.DatabaseClient;
import org.springframework.fu.jafu.ConfigurationDsl;
import org.springframework.fu.jafu.JafuApplication;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static org.springframework.fu.jafu.Jafu.webApplication;
import static org.springframework.fu.jafu.r2dbc.H2R2dbcDsl.r2dbcH2;
import static org.springframework.fu.jafu.web.WebFluxServerDsl.server;

public class FuApplication {

	public static void main(String[] args) {

		Consumer<ConfigurationDsl> dataConfig =
			conf -> conf
				.beans(beans -> beans.bean(UserRepository.class))
				.enable(r2dbcH2());

		Consumer<ConfigurationDsl> webConfig =
			conf -> conf
				.beans(beans -> beans.bean(UserHandler.class))
				.enable(server(server ->

					server
						.router(router -> {
							UserHandler userHandler = conf.ref(UserHandler.class);
							router.GET("/", userHandler::listApi);
						})
						.codecs(codecs -> codecs.string().jackson())
				));

		JafuApplication app = webApplication(x -> x
			.enable(dataConfig)
			.enable(webConfig)
			.listener(ApplicationReadyEvent.class, e -> x.ref(UserRepository.class).init())
		);


		app.run(args);
	}
}

class UserRepository {
	private final DatabaseClient client;

	public UserRepository(DatabaseClient client) {
		this.client = client;
	}

	public Mono<Integer> count() {
		return client.execute().sql("SELECT COUNT(*) FROM users").as(Integer.class).fetch().one();
	}

	public Flux<User> findAll() {
		return client.select().from("users").as(User.class).fetch().all();
	}

	public Mono<User> findOne(String id) {
		return client
			.execute()
			.sql("SELECT * FROM users WHERE login = $1").bind(1, id).as(User.class).fetch().one();
	}

	public Mono<Void> deleteAll() {
		return client.execute().sql("DELETE FROM users").fetch().one().then();
	}

	public Mono<String> save(User user) {
		return client.insert().into(User.class).table("users").using(user)
			.map((r, m) -> r.get("login", String.class)).one();
	}

	public void init() {
		client.execute().sql("CREATE TABLE IF NOT EXISTS users (login varchar PRIMARY KEY, firstname varchar, lastname varchar);").then()
			.then(deleteAll())
			.then(save(new User("smaldini", "Stéphane", "Maldini")))
			.then(save(new User("sdeleuze", "Sébastien", "Deleuze")))
			.then(save(new User("jlong", "Joshua", "Long")))
			.then(save(new User("bclozel", "Brian", "Clozel")))
			.block();
	}
}

class UserHandler {

	private final UserRepository repository;

	public UserHandler(UserRepository repository) {
		this.repository = repository;
	}

	public Mono<ServerResponse> listApi(ServerRequest request) {
		return ServerResponse.ok()
			.contentType(MediaType.APPLICATION_JSON_UTF8)
			.body(repository.findAll(), User.class);
	}
}

@Data
@NoArgsConstructor
class User {

	private String login;
	private String firstname;
	private String lastname;

	User(String login, String firstname, String lastname) {
		this.login = login;
		this.firstname = firstname;
		this.lastname = lastname;
	}
}


