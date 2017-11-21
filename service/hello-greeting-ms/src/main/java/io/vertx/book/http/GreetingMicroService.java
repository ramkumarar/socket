package io.vertx.book.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.rx.java.RxHelper;
import rx.Observable;
import rx.Scheduler;

import java.util.concurrent.TimeUnit;

public class GreetingMicroService extends AbstractVerticle {

    @Override
    public void start() {
        Router router= Router.router(vertx);
        router.get("/").handler(this::hello);
        router.get("/:name").handler(this::hello);

        vertx.createHttpServer()
       /* vertx.createHttpServer(new HttpServerOptions().setKeyStoreOptions(new JksOptions()
                .setPath("cert/server-keystore.jks")
                .setPassword("wibble"))
                .setSsl(true)        )*/
                .requestHandler(router::accept)
                .listen(9443);

        SockJSHandlerOptions options = new SockJSHandlerOptions().setHeartbeatInterval(2000);
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx, options);
        BridgeOptions bo = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("/client.register"))
                .addOutboundPermitted(new PermittedOptions().setAddress("service.ui-message"));
        sockJSHandler.bridge(bo, event -> {
            System.out.println("A websocket event occurred: " + event.type() + "; " + event.getRawMessage());
            event.complete(true);
        });

        router.route("/client.register" + "/*").handler(sockJSHandler);


        Scheduler scheduler = RxHelper.scheduler(vertx);
        Observable<Long> timer = Observable.interval(1000, 3000, TimeUnit.MILLISECONDS, scheduler);

        timer.subscribe(t -> {
            JsonObject m = new JsonObject();
            m.put("item",t);
            m.put("name",Thread.currentThread().getName());

            vertx.eventBus().publish("service.ui-message", m.encode());


        });




    }

    private void hello(RoutingContext rc) {
        String message="Hello";
        if(rc.pathParam("name")!=null){
            message += rc.pathParam("name");
        }

        JsonObject jsonObject= new JsonObject().put("message",message);
        rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                     .end(jsonObject.encode());

    }

}
