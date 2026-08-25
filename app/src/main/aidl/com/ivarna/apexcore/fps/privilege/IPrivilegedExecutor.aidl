package com.ivarna.apexcore.fps.privilege;

import android.os.Bundle;

/** Narrow command executor used by the ApexCore Shizuku UserService. */
interface IPrivilegedExecutor {
    Bundle execute(String command, long timeoutMs);
    int uid();
    void destroy();
}
