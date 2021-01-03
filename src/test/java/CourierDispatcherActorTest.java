import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.cloudkitchens.codechallenge.courier.CourierDispatcherActor;
import com.cloudkitchens.codechallenge.order.OrderActor;
import com.cloudkitchens.codechallenge.order.PlacedOrder;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.*;

public class CourierDispatcherActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testCourierLifeCycle(){

        String orderId = UUID.randomUUID().toString();

        TestProbe<OrderActor.Message> order =
                testKit.createTestProbe(OrderActor.Message.class);

        ActorRef<CourierDispatcherActor.Message> courier = testKit.spawn(CourierDispatcherActor.create(order.getRef(),
                new PlacedOrder(orderId,"order", "hot", 1, 0.9F)));


        OrderActor.PickupOrderCommand pickupOderCommand = (OrderActor.PickupOrderCommand)order.receiveMessage(Duration.ofMillis(6100));

        courier.tell(CourierDispatcherActor.ConfirmOrderAvailable.INSTANCE);

        OrderActor.OrderDeliveredEvent deliveredEvent =  (OrderActor.OrderDeliveredEvent)order.receiveMessage();

    }

}
