package android.net;

import android.os.Parcel;
import java.util.Collections;
import java.util.List;

public final class TestUri extends Uri {
    private final String value;

    public TestUri(String value) {
        this.value = value;
    }

    @Override public Builder buildUpon() { throw new UnsupportedOperationException(); }
    @Override public String getAuthority() { return null; }
    @Override public String getEncodedAuthority() { return null; }
    @Override public String getEncodedFragment() { return null; }
    @Override public String getEncodedPath() { return null; }
    @Override public String getEncodedQuery() { return null; }
    @Override public String getEncodedSchemeSpecificPart() { return null; }
    @Override public String getEncodedUserInfo() { return null; }
    @Override public String getFragment() { return null; }
    @Override public String getHost() { return null; }
    @Override public String getLastPathSegment() { return null; }
    @Override public String getPath() { return null; }
    @Override public List<String> getPathSegments() { return Collections.emptyList(); }
    @Override public int getPort() { return -1; }
    @Override public String getQuery() { return null; }
    @Override public String getScheme() { return "content"; }
    @Override public String getSchemeSpecificPart() { return value; }
    @Override public String getUserInfo() { return null; }
    @Override public boolean isHierarchical() { return true; }
    @Override public boolean isRelative() { return false; }
    @Override public String toString() { return value; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel destination, int flags) {
        throw new UnsupportedOperationException();
    }
}
