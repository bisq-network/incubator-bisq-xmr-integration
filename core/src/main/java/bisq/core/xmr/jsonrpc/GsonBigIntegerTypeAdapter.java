package bisq.core.xmr.jsonrpc;

import java.io.IOException;
<<<<<<< Upstream, based on branch 'master' of https://github.com/bisq-network/incubator-bisq-xmr-integration.git
<<<<<<< Upstream, based on branch 'master' of https://github.com/bisq-network/incubator-bisq-xmr-integration.git
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Type adapter for GSON which interprets all numbers as BigIntegers in order to preserve precision.
 */
public class GsonBigIntegerTypeAdapter extends TypeAdapter<Object> {
  
  private final TypeAdapter<Object> delegate = new Gson().getAdapter(Object.class);

  @Override
  public void write(JsonWriter out, Object value) throws IOException {
    delegate.write(out, value);
  }

  @Override
  public Object read(JsonReader in) throws IOException {
    JsonToken token = in.peek();
    switch (token) {
      case BEGIN_ARRAY:
        List<Object> list = new ArrayList<Object>();
        in.beginArray();
        while (in.hasNext()) {
          list.add(read(in));
        }
        in.endArray();
        return list;

      case BEGIN_OBJECT:
        Map<String, Object> map = new LinkedTreeMap<String, Object>();
        in.beginObject();
        while (in.hasNext()) {
          map.put(in.nextName(), read(in));
        }
        in.endObject();
        return map;

      case STRING:
        return in.nextString();

      case NUMBER:
        //return in.nextDouble();
        String n = in.nextString();
        return new BigDecimal(n).toBigIntegerExact();
=======
=======
import java.math.BigDecimal;
>>>>>>> 06e6fd6 GsonBigIntegerTypeAdapter - Fixed adapter failing for numbers defaulting to scientific (exponential) notation. JsonUtils - Methods with no adapter call equivalent methods by passing the default adapter.
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Type adapter for GSON which interprets all numbers as BigIntegers in order to preserve precision.
 */
public class GsonBigIntegerTypeAdapter extends TypeAdapter<Object> {
  
  private final TypeAdapter<Object> delegate = new Gson().getAdapter(Object.class);

  @Override
  public void write(JsonWriter out, Object value) throws IOException {
    delegate.write(out, value);
  }

  @Override
  public Object read(JsonReader in) throws IOException {
    JsonToken token = in.peek();
    switch (token) {
      case BEGIN_ARRAY:
        List<Object> list = new ArrayList<Object>();
        in.beginArray();
        while (in.hasNext()) {
          list.add(read(in));
        }
        in.endArray();
        return list;

      case BEGIN_OBJECT:
        Map<String, Object> map = new LinkedTreeMap<String, Object>();
        in.beginObject();
        while (in.hasNext()) {
          map.put(in.nextName(), read(in));
        }
        in.endObject();
        return map;

      case STRING:
        return in.nextString();

      case NUMBER:
        //return in.nextDouble();
        String n = in.nextString();
<<<<<<< Upstream, based on branch 'master' of https://github.com/bisq-network/incubator-bisq-xmr-integration.git
        return new BigInteger(n);
>>>>>>> 3bd2298 Code review updates.
=======
        return new BigDecimal(n).toBigIntegerExact();
>>>>>>> 06e6fd6 GsonBigIntegerTypeAdapter - Fixed adapter failing for numbers defaulting to scientific (exponential) notation. JsonUtils - Methods with no adapter call equivalent methods by passing the default adapter.

      case BOOLEAN:
        return in.nextBoolean();

      case NULL:
        in.nextNull();
        return null;

      default:
        throw new IllegalStateException();
    }
  }
}