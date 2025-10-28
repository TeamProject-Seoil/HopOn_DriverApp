// app/src/main/java/com/example/driver_bus_info/util/Nav.java
package com.example.driver_bus_info.util;

import android.content.Context;
import android.content.Intent;

import com.example.driver_bus_info.activity.DrivingActivity;

public final class Nav {
    private Nav() {}

    public static void goDriving(Context ctx, Long operationId, String vehicleId, String routeId, String routeName) {
        Intent i = new Intent(ctx, DrivingActivity.class);
        if (operationId != null) i.putExtra("operationId", operationId);
        if (vehicleId   != null) i.putExtra("vehicleId", vehicleId);
        if (routeId     != null) i.putExtra("routeId", routeId);
        if (routeName   != null) i.putExtra("routeName", routeName);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ctx.startActivity(i);
    }
}
