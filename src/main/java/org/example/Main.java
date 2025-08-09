package org.example;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 30);
        crptApi.createDocument(new CrptApi.Document("XML", "Milk Piskarevskoe"
                , "8", "Ввод в оборот. Производство РФ. xml"), "123312434QQQ");
    }
}