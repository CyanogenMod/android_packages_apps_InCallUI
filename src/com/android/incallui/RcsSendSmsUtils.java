/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.android.incallui;

import android.database.Cursor;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import java.util.List;

public class RcsSendSmsUtils {
    /**
     * Start the ACTION_SENDTO activity.
     *
     * @param context
     * @param phoneNumber
     * @param message
     */
    public static void startSendSmsActivity(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setType("vnd.android-dir/mms-sms");
        if (isActivityIntentAvailable(context, intent)) {
            context.startActivity(intent);
        }
    }

    /**
     * Start the ACTION_SENDTO activity.
     *
     * @param context
     * @param phoneNumber
     * @param message
     */
    public static void startSendSmsActivity(Context context, String[] phoneNumbers) {
        if (phoneNumbers == null || phoneNumbers.length == 0) {
            return;
        }

        for (String phoneNumber : phoneNumbers) {
            if (!PhoneNumberUtils.isGlobalPhoneNumber(phoneNumber)) {
                return;
            }
        }

        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < phoneNumbers.length; i++) {
            buffer.append(phoneNumbers[i]);

            if ((i + 1) < phoneNumbers.length) {
                buffer.append(";");
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("address", buffer.toString());
        intent.setType("vnd.android-dir/mms-sms");
        if (isActivityIntentAvailable(context, intent)) {
            context.startActivity(intent);
        }
    }

    public static boolean isActivityIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static int getUnReadMessageCount(Context context, String phoneNumber) {

        if (phoneNumber == null) {
            return 0;
        }
        Cursor cursor = context.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[] { Telephony.MmsSms._ID },
            "read=? AND " + Telephony.Sms.ADDRESS + "=?", new String[] { String.valueOf(0), phoneNumber }, null);

        int count = 0;
        if (cursor != null) {
            try {
                count = cursor.getCount();
            } finally {
                cursor.close();
            }
        }
        return count;
    }
}
