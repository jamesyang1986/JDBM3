package jdbm;

import java.io.*;
import java.util.Map;

import org.junit.Test;

public class TestInsertUpdate extends TestCaseWithTestFile {
        
        /** Test that the object is not modified by serialization. 
         * @throws IOException
         */
        @Test
        public void testInsertUpdateWithCustomSerializer () throws IOException {
        RecordManager recman = newRecordManager();
        Serializer<Long> serializer = new Serializer<Long>(){

            public void serialize(DataOutput out, Long obj) throws IOException {
                out.writeLong(obj);
            }

            public Long deserialize(DataInput in) throws IOException, ClassNotFoundException {
                return in.readLong();
            }
        };
                
        Map<Long, Long> map = recman.createHashMap("custom", serializer, serializer);
        
        map.put(new Long(1), new Long(1));
        map.put(new Long(2), new Long(2));
        recman.commit();
        map.put(new Long(2), new Long(3));
        recman.commit(); 
                recman.close();
        }

}