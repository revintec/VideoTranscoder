package com.revin.util;

/**
 * Created by revin on Dec.29,2014.
 */
public class Test{
    protected static boolean a=true;
    public static boolean c(){
        try{
            return a;
        }finally{
           a=false;
        }
    }
    public static void main(String[] args){
        System.out.println(c());
        System.out.println(c());
    }
}
