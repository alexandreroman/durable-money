package io.temporal.demos.durablemoney.account;

import java.util.regex.Pattern;

final class XidValidator {
    private static final Pattern XID_PATTERN =
            Pattern.compile("^transfer-[0-9a-f-]{36}-(debit|credit|journal)$");

    private XidValidator() {}

    static String requireValid(String xid) {
        if (xid == null || !XID_PATTERN.matcher(xid).matches()) {
            throw new IllegalArgumentException("Invalid xid: " + xid);
        }
        return xid;
    }
}
