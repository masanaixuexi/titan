/**
 *    Auth:riozenc
 *    Date:2019年3月5日 下午3:47:32
 *    Title:org.gateway.filter.BemServerFilter2.java
 **/
package org.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import org.gateway.custom.CustomServerHttpRequest;
import org.gateway.handler.AuthorizationHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.DefaultServerRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netty.buffer.ByteBufAllocator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class BemServerFilter2 implements GatewayFilter, Ordered {
	@Autowired
	private AuthorizationHandler authorizationHandler;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		CustomServerHttpRequest customServerHttpRequest = new CustomServerHttpRequest(exchange.getRequest());

		try {
			ServerRequest serverRequest = new DefaultServerRequest(exchange);
			String userId = getUserId();
			String roleIds = getRoleId(userId);
			int length = userId.length() + roleIds.length() + 2;
			// TODO: flux or mono

//			Flux<String> modifiedBody = serverRequest.bodyToFlux(String.class).flatMap(body -> {
//
//				if (isApplicationJsonType(exchange.getRequest())) {
//					body = tamperWithJson(body, userId, roleIds);
//				} else {
//					body = tamperWithForm(body, userId, roleIds);
//				}
//				
//				DataBuffer bodyDataBuffer = stringBuffer(body);
//				Flux<DataBuffer> bodyFlux = Flux.just(bodyDataBuffer);
//				return Flux.just(body);
//			});

			Mono<String> modifiedBody = serverRequest.bodyToMono(String.class).flatMap(body -> {

				if (isApplicationJsonType(exchange.getRequest())) {
					body = tamperWithJson(body, userId, roleIds);
				} else {
					body = tamperWithForm(body, userId, roleIds);
				}

				return Mono.just(body);
			});
			StringBuilder stringBuilder = new StringBuilder();
			modifiedBody.subscribe(value -> {
				stringBuilder.append(value);
			});

			DataBuffer bodyDataBuffer = stringBuffer(stringBuilder.toString());
			Flux<DataBuffer> bodyFlux = Flux.just(bodyDataBuffer);

			customServerHttpRequest.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
			customServerHttpRequest.header("Content-Length",
					String.valueOf(exchange.getRequest().getHeaders().getContentLength() + length));
			customServerHttpRequest.body(bodyFlux);
			return chain.filter(exchange.mutate().request(customServerHttpRequest.build()).build());

		} catch (Exception e) {
			// TODO Auto-generated catch block
			return Mono.error(e);
		}

	}

	private String getUserId() throws Exception {
		return authorizationHandler.getUser();
	}

	private String getRoleId(String userId) throws Exception {
		return authorizationHandler.getRoles(userId);
	}

	private boolean isApplicationJsonType(ServerHttpRequest serverHttpRequest) {
		MediaType mediaType = serverHttpRequest.getHeaders().getContentType();
		if (mediaType == null) {
			return true;
		}
		if (!mediaType.includes(MediaType.APPLICATION_FORM_URLENCODED)) {
			return true;
		}
		return mediaType.includes(MediaType.APPLICATION_JSON);
	}

	private String tamperWithJson(String body, String userId, String roleIds) {
		Gson gson = new Gson();
		JsonElement jsonElement = body == null ? new JsonObject() : gson.fromJson(body, JsonElement.class);
		if (jsonElement.isJsonObject()) {
			jsonElement.getAsJsonObject().addProperty(AuthorizationHandler.USER_ID, userId);
			jsonElement.getAsJsonObject().addProperty(AuthorizationHandler.ROLE_IDS, roleIds);
		}
		return jsonElement.toString();
	}

	private String tamperWithForm(String body, String userId, String roleIds) {
		return new StringBuilder(null == body ? "" : body).append("&").append(AuthorizationHandler.USER_ID).append("=")
				.append(userId).append("&").append(AuthorizationHandler.ROLE_IDS).append("=").append(roleIds)
				.toString();
	}

	private DataBuffer stringBuffer(String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

		NettyDataBufferFactory nettyDataBufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);
		DataBuffer buffer = nettyDataBufferFactory.allocateBuffer(bytes.length);
		buffer.write(bytes);
		return buffer;
	}

	@Override
	public int getOrder() {
		// TODO Auto-generated method stub
		return HIGHEST_PRECEDENCE;
	}
}
