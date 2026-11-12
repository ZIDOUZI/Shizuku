package rikka.shizuku.server.util;

import android.content.pm.PackageInfo;
import android.content.pm.ParceledListSlice;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.util.SystemServiceBinder;

public final class InstalledPackagesCompat {
    private static final SystemServiceBinder<IInterface> PACKAGE_MANAGER =
            new SystemServiceBinder<>("package", InstalledPackagesCompat::asPackageManager);

    private InstalledPackagesCompat() {
    }

    public static List<PackageInfo> getInstalledPackages(long flags, int userId)
            throws RemoteException {
        try {
            ParceledListSlice<PackageInfo> slice =
                    PackageManagerApis.getInstalledPackages(flags, userId);
            if (slice == null || slice.getList() == null) {
                return Collections.emptyList();
            }
            return slice.getList();
        } catch (NoSuchMethodError e) {
            return getInstalledPackagesAfterReturnTypeChange(flags, userId, e);
        }
    }

    public static List<PackageInfo> getInstalledPackagesNoThrow(long flags, int userId) {
        try {
            return getInstalledPackages(flags, userId);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static List<PackageInfo> getInstalledPackagesAfterReturnTypeChange(
            long flags, int userId, NoSuchMethodError previousFailure) throws RemoteException {
        IInterface packageManager = PACKAGE_MANAGER.get();
        if (packageManager == null) {
            throw previousFailure;
        }

        try {
            Method method = packageManager.getClass()
                    .getMethod("getInstalledPackages", long.class, int.class);
            return unwrap(method.invoke(packageManager, flags, userId));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RemoteException) {
                throw (RemoteException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Failed to get installed packages", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to get installed packages", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<PackageInfo> unwrap(Object slice) throws ReflectiveOperationException {
        if (slice == null) {
            return Collections.emptyList();
        }

        Object list = slice.getClass().getMethod("getList").invoke(slice);
        return list == null
                ? Collections.emptyList()
                : (List<PackageInfo>) list;
    }

    private static IInterface asPackageManager(IBinder binder) {
        try {
            return (IInterface) Class.forName("android.content.pm.IPackageManager$Stub")
                    .getMethod("asInterface", IBinder.class)
                    .invoke(null, binder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create IPackageManager", e);
        }
    }
}
