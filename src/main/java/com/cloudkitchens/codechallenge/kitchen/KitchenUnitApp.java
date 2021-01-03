package com.cloudkitchens.codechallenge.kitchen;

import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.JsonFraming;
import akka.stream.javadsl.Sink;
import akka.stream.typed.javadsl.ActorSink;
import com.cloudkitchens.codechallenge.order.OrderVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;

import java.nio.file.Paths;
import java.time.Duration;

public class KitchenUnitApp {

    public static void main(String[] args) {

        Config conf = KitchenUnitConfig.config;

        final ActorSystem<KitchenUnitActor.Message> kitchenUnit = ActorSystem.create(KitchenUnitActor.create(), "kitchenUnit");

        Sink<KitchenUnitActor.Message, NotUsed> kitchenUnitSink =
                ActorSink.actorRef(kitchenUnit, KitchenUnitActor.UpstreamCompleted.INSTANCE, e -> KitchenUnitActor.UpstreamFailed.INSTANCE);

        System.out.println(KitchenUnitConfig.config.getInt("order-ingestion-rate-per-second"));

        // Source stream being the file containing the orders
        FileIO.fromPath(Paths.get(conf.getString("orders-file-location")))
                // Frame input based on jason objects
                .via(JsonFraming.objectScanner(1024))
                // Convert from bytstring to utf8 string
                .map(e -> e.utf8String())
                // Parse JSON into a VO object
                .map(e -> new ObjectMapper().readValue(e, OrderVo.class))
                // Wrap the VO into a Kitchen Unit Order command object
                .map(e -> new KitchenUnitActor.OrderRequest(e))
                .map(e -> (KitchenUnitActor.Message)e)
                // Throttle the request configurable number of requests per second
                .throttle(conf.getInt("order-ingestion-rate-per-second"), Duration.ofSeconds(1))
                // Sink requests to the Kitchen Unit sink that is ultimately responsible to handle the order requests
                .runWith(kitchenUnitSink, kitchenUnit);

    }

}
