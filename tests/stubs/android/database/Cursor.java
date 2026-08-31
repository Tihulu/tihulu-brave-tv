package android.database; public interface Cursor { boolean moveToFirst(); int getInt(int i); int getColumnIndexOrThrow(String n); void close(); }
