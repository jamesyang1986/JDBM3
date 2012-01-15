/*******************************************************************************
 * Copyright 2010 Cees De Groot, Alex Boisvert, Jan Kotek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package net.kotek.jdbm;

import javax.crypto.*;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOError;
import java.io.IOException;
import java.security.spec.KeySpec;

/**
 *
 */
public class DBMaker {

    private byte cacheType = DBCache.MRU;
    private int mruCacheSize = 2048;

    private String location = null;

    private boolean disableTransactions = false;
    private boolean readonly = false;
    private String password = null;
    private boolean useRandomAccessFile = false;
    private boolean autoClearRefCacheOnLowMem = true;


    /**
     * Creates new DBMaker and sets location where database is located.
     * <p>
     * If location is null, in-memory store will be used. In this case data will be
     * lost after JVM exits.
     *
     * @param location on disk where db is located, Null for in-memory store
     */
    public DBMaker(String location) {
        this.location = location;
    }


    /**
     * Use WeakReference for cache.
     * This cache does not improve performance much,
     * but prevents JDBM from creating multiple instances of the same object.
     *
     * @return this builder
     */
    public DBMaker enableWeakCache() {
        cacheType = DBCache.WEAK;
        return this;
    }

    /**
     * Use SoftReference for cache.
     * This cache greatly improves performance if you have enoguth memory.
     * Instances in cache are Garbage Collected when memory gets low
     *
     * @return this builder
     */
    public DBMaker enableSoftCache() {
        cacheType = DBCache.SOFT;
        return this;
    }

    /**
     * Use hard reference for cache.
     * This greatly improves performance if there is enought memory
     * Hard cache has smaller memory overhead then Soft or Weak, because
     * reference objects and queue does not have to be maintained
     *
     * @return this builder
     */
    public DBMaker enableHardCache() {
        cacheType = DBCache.SOFT;
        return this;
    }


    /**
     * Use 'Most Recently Used' cache with limited size.
     * Oldest instances are released from cache when new instances are fetched.
     * This cache is not cleared by GC. Is good for systems with limited memory.
     * <p/>
     * Default size for MRU cache is 2048 records.
     *
     * @return this builder
     */
    public DBMaker enableMRUCache() {
        cacheType = DBCache.MRU;
        return this;
    }

    /**
     *
     * Sets 'Most Recently Used' cache size. This cache is activated by default with size 2048
     *
     * @param cacheSize number of instances which will be kept in cache.
     * @return this builder
     */
    public DBMaker setMRUCacheSize(int cacheSize) {
        if (cacheSize < 0) throw new IllegalArgumentException("Cache size is smaller than zero");
        cacheType = DBCache.MRU;
        return this;
    }

    /**
     * If reference (soft,weak or hard) cache is enabled,
     * GC may not release references fast enough (or not at all in case of hard cache).
     * So JDBM periodically checks amount of free heap memory.
     * If free memory is less than 25% or 10MB,
     * JDBM completely clears its reference cache to prevent possible memory issues.
     * <p>
     * Calling this method disables auto cache clearing when mem is low.
     * And of course it can cause some out of memory exceptions.
     *
     * @return this builder
     */
    public DBMaker disableCacheAutoClear(){
        this.autoClearRefCacheOnLowMem = false;
        return this;
    }


    /**
     * Enabled storage encryption using AES cipher
     * Storage can not be read, unless the key is provided next time it is opened
     *
     * @param password
     * @return this builder
     */
    public DBMaker enableEncryption(String password) {
        this.password = password;
        return this;
    }


    /**
     * Make DB readonly.
     * Update/delete/insert operation will throw 'UnsupportedOperationException'
     *
     * @return this builder
     */
    public DBMaker readonly() {
        readonly = true;
        return this;
    }


    /**
     * Disable cache completely
     *
     * @return this builder
     */
    public DBMaker disableCache() {
        cacheType = DBCache.NONE;
        return this;
    }


    /**
     * Option to disable transaction (to increase performance at the cost of potential data loss).
     * Transactions are enabled by default
     * <p/>
     * Switches off transactioning for the record manager. This means
     * that a) a transaction log is not kept, and b) writes aren't
     * synch'ed after every update. Writes are cached in memory and then flushed
     * to disk every N writes. You may also flush writes manually by calling commit().
     * This is useful when batch inserting into a new database.
     * <p/>
     * When using this, database must be properly closed before JVM shutdown.
     * Failing to do so may and WILL corrupt store.
     *
     * @return this builder
     */
    public DBMaker disableTransactions() {
        this.disableTransactions = true;
        return this;
    }

    /**
     * By default JDBM uses mapped memory buffers to read from files.
     * But this may behave strangely on some platforms.
     * Safe alternative is to use old RandomAccessFile rather then mapped ByteBuffer.
     * There is typically slower (pages needs to be copyed into memory on every write).
     *
     * @return this builder
     */
    public DBMaker useRandomAccessFile(){
        this.useRandomAccessFile = true;
        return this;
    }
    

    /**
     * Opens database with settings earlier specified in this builder.
     *
     * @return new DB
     * @throws java.io.IOError if db could not be opened
     */
    public DB build() {

        Cipher cipherIn = null;
        Cipher cipherOut = null;
        if (password != null) try {
            //initialize ciphers
            //this code comes from stack owerflow
            //http://stackoverflow.com/questions/992019/java-256bit-aes-encryption/992413#992413
            byte[] salt = new byte[]{3, -34, 123, 53, 78, 121, -12, -1, 45, -12, -48, 89, 11, 100, 99, 8};
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 1024, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

            String transform = "AES/CBC/NoPadding";
            IvParameterSpec params = new IvParameterSpec(salt);

            cipherIn = Cipher.getInstance(transform);
            cipherIn.init(Cipher.ENCRYPT_MODE, secret, params);

            cipherOut = Cipher.getInstance(transform);
            cipherOut.init(Cipher.DECRYPT_MODE, secret, params);

            //sanity check, try with page size
            byte[] data = new byte[Storage.BLOCK_SIZE];
            byte[] encData = cipherIn.doFinal(data);
            if (encData.length != Storage.BLOCK_SIZE)
                throw new Error("Block size changed after encryption, make sure you use '/NoPadding'");
            byte[] data2 = cipherOut.doFinal(encData);
            for (int i = 0; i < data.length; i++) {
                if (data[i] != data2[i]) throw new Error();
            }

        } catch (Exception e) {
            throw new IOError(e);
        }

        DBAbstract db = null;

        try {
            db = new DBStore(location, readonly, disableTransactions, cipherIn, cipherOut,useRandomAccessFile);
        } catch (IOException e) {
            throw new IOError(e);
        }



        if (cacheType == DBCache.MRU) {
            db = new DBCache((DBStore) db, mruCacheSize, cacheType,autoClearRefCacheOnLowMem);
        } else if (cacheType == DBCache.SOFT) {
            db = new DBCache((DBStore) db, mruCacheSize, cacheType,autoClearRefCacheOnLowMem);
        } else if (cacheType == DBCache.HARD) {
            db = new DBCache((DBStore) db, mruCacheSize, cacheType,autoClearRefCacheOnLowMem);

        } else if (cacheType == DBCache.WEAK) {
            db = new DBCache((DBStore) db, mruCacheSize, cacheType, autoClearRefCacheOnLowMem);

        } else if (cacheType == DBCache.NONE) {
            //do nothing
        } else {
            throw new IllegalArgumentException("Unknown cache type: " + cacheType);
        }


        return db;
    }

}
