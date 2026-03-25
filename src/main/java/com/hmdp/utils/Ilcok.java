package com.hmdp.utils;

public interface Ilcok {

//    尝试获取锁
//    @param timeoutSec 锁持有的超时时间，过期后自动释放
//    @return true 代表取锁成功，false代表取锁失败
//
    boolean tryLock(long timeoutSec);


    void unLock();


}
