package com.mfp.filemanager.shizuku;

import android.os.ParcelFileDescriptor;

/** File operations executed inside the Shizuku "user service" process (shell uid). */
interface IShizukuFileService {
    /** Reserved transaction id: Shizuku calls this when the service must stop. */
    void destroy() = 16777114;

    /** Entries of a folder: "dir\u001Fsize\u001Fmtime\u001FchildCount\u001Fname", joined by \u001E. Null on failure. */
    String list(String path) = 1;
    boolean exists(String path) = 2;
    boolean delete(String path) = 3;
    boolean rename(String from, String to) = 4;
    boolean mkdirs(String path) = 5;
    ParcelFileDescriptor openRead(String path) = 6;
    ParcelFileDescriptor openWrite(String path) = 7;
}
