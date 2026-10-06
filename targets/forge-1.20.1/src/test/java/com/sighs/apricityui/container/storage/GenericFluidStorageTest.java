package com.sighs.apricityui.container.storage;

import com.sighs.apricityui.stack.FluidKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GenericFluidStorageTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final FluidKey WATER = new FluidKey(Fluids.WATER, null);

    @Test
    void oneTankTransfersOnlyAcceptedAmount() {
        Tank handler = new Tank(1, 600);
        GenericStorage storage = GenericStorages.fluidHandler(handler);

        assertEquals(400, storage.insert(0, WATER, 500, true));
        assertEquals(600, handler.amount);
        assertEquals(400, storage.insert(0, WATER, 500, false));
        assertEquals(1000, handler.amount);
        assertEquals(250, storage.extract(0, WATER, 250, true));
        assertEquals(1000, handler.amount);
        assertEquals(250, storage.extract(0, WATER, 250, false));
        assertEquals(750, handler.amount);
        assertEquals(0, storage.insert(1, WATER, 100, false));
        assertNull(storage.resourceType(1));
    }

    @Test
    void multiTankGenericHandlerCannotTransferByIndex() {
        Tank handler = new Tank(2, 600);
        GenericStorage storage = GenericStorages.fluidHandler(handler);

        assertEquals(0, storage.insert(0, WATER, 100, false));
        assertEquals(0, storage.extract(0, WATER, 100, false));
        assertEquals(0, storage.insert(1, WATER, 100, false));
        assertEquals(0, storage.extract(1, WATER, 100, false));
        assertEquals(600, handler.amount);
    }

    private static final class Tank implements IFluidHandler {
        private final int tanks;
        private int amount;

        private Tank(int tanks, int amount) {
            this.tanks = tanks;
            this.amount = amount;
        }

        @Override public int getTanks() { return tanks; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 && amount > 0 ? WATER.toStack(amount) : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { return 1000; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0; }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (!WATER.equals(FluidKey.of(resource))) return 0;
            int accepted = Math.min(resource.getAmount(), 1000 - amount);
            if (action.execute()) amount += accepted;
            return accepted;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!WATER.equals(FluidKey.of(resource))) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }
        @Override public FluidStack drain(int requested, FluidAction action) {
            int drained = Math.min(requested, amount);
            if (action.execute()) amount -= drained;
            return drained == 0 ? FluidStack.EMPTY : WATER.toStack(drained);
        }
    }
}
