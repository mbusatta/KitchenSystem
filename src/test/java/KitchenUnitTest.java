import akka.actor.testkit.typed.Effect;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import com.cloudkitchens.codechallenge.kitchen.KitchenUnitActor;
import com.cloudkitchens.codechallenge.order.OrderVo;
import com.cloudkitchens.codechallenge.order.PlacedOrder;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KitchenUnitTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testKitchenUnitLifecycle(){

        OrderVo orderVo = new OrderVo();

        orderVo.setId(UUID.randomUUID().toString());
        orderVo.setName("some order");
        orderVo.setTemp("cold");
        orderVo.setShelfLife(5);
        orderVo.setDecayRate(0.7F);


        BehaviorTestKit<KitchenUnitActor.Message> kitchenUnit = BehaviorTestKit.create(KitchenUnitActor.create());

        assertEquals("shelvesManager", kitchenUnit.expectEffectClass(Effect.Spawned.class).childName());

        kitchenUnit.run(new KitchenUnitActor.OrderRequest(orderVo));

        assertEquals("Order:"+ orderVo.getId() ,kitchenUnit.expectEffectClass(Effect.Spawned.class).childName());

        kitchenUnit.run(new KitchenUnitActor.OrderDelivered(new PlacedOrder(orderVo.getId(), orderVo.getName(),
                orderVo.getTemp(), orderVo.getShelfLife(), orderVo.getDecayRate())));

        assertTrue(kitchenUnit.isAlive());

        kitchenUnit.run(KitchenUnitActor.UpstreamCompleted.INSTANCE);

        assertFalse(kitchenUnit.isAlive());

    }

}
