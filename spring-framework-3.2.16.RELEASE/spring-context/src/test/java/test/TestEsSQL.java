package test;

/**
 * Created by wangyingjie1 on 2017/1/9.
 */
public class TestEsSQL {

    public static void main(String[] args) {

        String  sql = "{\"query\": {\"bool\": {\"must\": {\"bool\": {\"must\": [{\"range\": {\"created\": " +
                "{\"from\": \"2017-01-01\",\"to\": null,\"include_lower\": false,\"include_upper\": true}}}," +
                "{\"match\": {\"status\": {\"query\": 1,\"type\": \"phrase\"}}}]}}}}}";
        System.out.println(sql.replaceAll("\n", "").replaceAll("\t", ""));
    }



}
