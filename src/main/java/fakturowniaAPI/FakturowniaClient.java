package fakturowniaAPI;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.Request;

import java.io.IOException;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Response;
import okhttp3.OkHttpClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.TemporalAdjusters.*;



//TODO use Log4j for logs

public class FakturowniaClient {


    String domainName;
    String token;
    private final OkHttpClient client = new OkHttpClient();

    private static final class HttpBuilder{
        private static final String mainDomain = ".fakturownia.pl/";
        private static final String formatType = "invoices.json?";
        private static final String urlPrefix = "https://";
        private static final String tokenPrefix = "&api_token=";
        private static final String periodPrefix = "period=";
        private static final String peroidMore = "period=more";
        private static final String dateFromPrefix ="&date_from=";
        private static final String dateToPrefix ="&date_to=";

        public static String createJsonURLfromToken(String token, String domainName){
            String url = urlPrefix
                    + domainName
                    + mainDomain
                    + formatType
                    + tokenPrefix
                    + token;

            return url;
        }

        public static String createJsonURLfromToken(String token,String domainName, Period period){

            String url = urlPrefix
                    + domainName
                    + mainDomain
                    + formatType
                    + periodPrefix
                    + period.getValue()
                    + tokenPrefix
                    + token;

            return url;
        }

        public static String createJsonURLfromToken(String token,String domainName, LocalDate dateFrom, LocalDate dateTo){

            String url = urlPrefix
                    + domainName
                    + mainDomain
                    + formatType
                    + peroidMore
                    + dateFromPrefix
                    + dateToString(dateFrom)
                    + dateToPrefix
                    + dateToString(dateTo)
                    + tokenPrefix
                    + token;

            return url;
        }

        private static String dateToString(LocalDate date){
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd_MM_yyyy");
            String formattedDate = date.format(formatter);
            return formattedDate;
        }

    }





    public FakturowniaClient(String token){
        this.domainName = token.substring(token.indexOf("/")+1,token.length());
        this.token =  token;
    }


    private  String getJSON(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();

        return response.body().string();
    }

    //region SUMMARIES

    //returns summary of a specific period

    public  InvoiceSummary getSummary(Period period) throws IOException{

        List<Invoice> invoiceList = getInvoices(period);

            double priceNet = 0;
            double priceGross = 0;
            double priceTax = 0;
            double exchangeRate = 1;

            for (Invoice invoice : invoiceList) {
                exchangeRate = Double.parseDouble(invoice.getExchangeRate());
                priceNet += Double.parseDouble(invoice.getPriceNet()) * exchangeRate;
                priceGross += Double.parseDouble(invoice.getPriceGross()) * exchangeRate;
                priceTax += Double.parseDouble(invoice.getPriceTax()) * exchangeRate;
            }
            long daysPassed = getDays(period);

            InvoiceSummary summary = new InvoiceSummary(priceNet, priceGross, priceTax, daysPassed);
            return summary;

    }


    public  InvoiceSummary getSummary(LocalDate dateFrom, LocalDate dateTo) throws IOException {
        double priceNet = 0;
        double priceGross = 0;
        double priceTax = 0;
        double exchangeRate = 1;
        long daysPassed = 0;


        List<Invoice> invoiceList = getInvoices(dateFrom, dateTo);

        for( Invoice invoice :invoiceList){
            exchangeRate = Double.parseDouble(invoice.getExchangeRate());
            priceNet += Double.parseDouble(invoice.getPriceNet()) * exchangeRate;
            priceGross += Double.parseDouble(invoice.getPriceGross()) * exchangeRate;
            priceTax += Double.parseDouble(invoice.getPriceTax()) *exchangeRate;
        }

        daysPassed = getDays(dateFrom,dateTo);

        InvoiceSummary summary = new InvoiceSummary(priceNet,priceGross,priceTax, daysPassed);
        return summary;
    }



    //endregion

    //region INVOICE GETTERS

    //returns ALL invoices form the first to the last
    public List<Invoice> getInvoices() throws IOException {
        String url = HttpBuilder.createJsonURLfromToken(token, domainName);

        String json = getJSON(url);

        List<Invoice> invoiceList = pharseJSON(json);

        return invoiceList;
    }

    //returns invoices from specific perioids
    public  List<Invoice> getInvoices(Period period) throws  IOException {
        String url = HttpBuilder.createJsonURLfromToken(token, domainName, period);

        String json = getJSON(url);

        List<Invoice> invoiceList = pharseJSON(json);

        return invoiceList;
    }

    //returns invoices from  a custom period
    public  List<Invoice> getInvoices(LocalDate dateFrom, LocalDate dateTo) throws IOException {
       String url = HttpBuilder.createJsonURLfromToken(token,domainName,dateFrom,dateTo);
       String json = getJSON(url);

       List<Invoice> invoiceList = pharseJSON(json);

       return invoiceList;
    }

    //endregion


    //region DATE THINGS

    private long getDays(LocalDate dateFrom, LocalDate dateTo){
        return DAYS.between(dateFrom,dateTo);

    }

    //calculate days by specific period
    private long getDays(Period period){
        long daysPassed = 0;
        LocalDate initial = LocalDate.now();

        if (period.getValue() == "this_month") {
            LocalDate start = initial
                    .withDayOfMonth(1);

            LocalDate end = initial;

            daysPassed= DAYS.between(start,end);
        }

        if (period.getValue() == "last_month") {
            LocalDate start = initial
                    .minusMonths(1)
                    .with(firstDayOfMonth());

            LocalDate end = initial
                    .minusMonths(1)
                    .with(lastDayOfMonth());

            daysPassed= DAYS.between(start,end);
        }

        if (period.getValue() == "this_year") {
            LocalDate start = initial
                    .with(firstDayOfYear());

            LocalDate end = initial;

            daysPassed= DAYS.between(start,end);
        }

        if (period.getValue() == "last_year") {
            LocalDate start = initial.minusYears(1)
                    .with(firstDayOfYear());

            LocalDate end = initial.minusYears(1)
                    .with(lastDayOfYear());

            daysPassed= DAYS.between(start,end);
        }

        return daysPassed;
    }

    //endregion

    private  List<Invoice> pharseJSON(String json){
        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<Invoice>>(){}.getType();
        List<Invoice> invoiceList = gson.fromJson(json, listType);
        return invoiceList;
    }

}
