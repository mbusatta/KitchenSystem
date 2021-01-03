import com.cloudkitchens.codechallenge.order.CookedPlacedOrder;
import com.cloudkitchens.codechallenge.order.PlacedOrder;
import com.cloudkitchens.codechallenge.shelf.Shelf;
import com.cloudkitchens.codechallenge.order.ShelfPlacedOrder;
import org.junit.Test;


import java.util.UUID;
import static org.junit.Assert.*;

public class ShelfTest {

    @Test
    public void testTotalExpirationMillis() {
        PlacedOrder placedOrder = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);

        Shelf shelf = new Shelf(Shelf.ShelfType.HOT, 2);

        ShelfPlacedOrder spo = shelf.placeOrder(new CookedPlacedOrder(placedOrder));

        assertEquals(7142L, spo.totalExpirationMillis);

    }

    @Test
    public void testOrderValue() throws InterruptedException {
        PlacedOrder placedOrder = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 2, 0.9F);

        Shelf shelf = new Shelf(Shelf.ShelfType.OVERFLOW, 2);

        ShelfPlacedOrder spo = shelf.placeOrder(new CookedPlacedOrder(placedOrder));

        assertFalse(spo.wasted());

        Thread.sleep(2000);

        assertTrue(spo.wasted());

    }

    @Test
    public void testShelfCapacity(){

        Shelf shelf = new Shelf(Shelf.ShelfType.HOT, 2);

        PlacedOrder placedOrder1 = getNewOrder();
        PlacedOrder placedOrder2 = getNewOrder();
        PlacedOrder placedOrder3 = getNewOrder();

        assertFalse(shelf.isFull());
        shelf.placeOrder(new CookedPlacedOrder(placedOrder1));
        assertFalse(shelf.isFull());
        shelf.placeOrder(new CookedPlacedOrder(placedOrder2));
        assertTrue(shelf.isFull());
        shelf.takeOrder(placedOrder1.id);
        assertFalse(shelf.isFull());
        shelf.placeOrder(new CookedPlacedOrder(placedOrder1));

        assertThrows(RuntimeException.class, () -> {
            shelf.placeOrder(new CookedPlacedOrder(placedOrder3));
        });

    }

    private PlacedOrder getNewOrder(){
        return new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 300, 0.45F);
    }


}
