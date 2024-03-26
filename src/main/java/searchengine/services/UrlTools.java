package searchengine.services;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("JavadocLinkAsPlainText")
public class UrlTools {
    private static final Pattern onlySiteUrlPattern = Pattern.compile("^http.?://[^/]+?$");
    private static final Pattern siteUrlPattern = Pattern.compile("(http.?://.+?)/.*");
    private static final List<String> nonHtmlExtensions = List.of(".pdf", ".jpg", ".jpeg", ".png", ".webp");

    /**
     * Метод проверяет, соответствует ли url значению типа http://www.site.com (без слэша в конце)
     *
     * @param url адрес
     * @return True, если это ссылка типа http://www.site.com (без слэша в конце)
     */
    public static boolean isSiteUrlOnly(String url) {
        Matcher urlMatcher = onlySiteUrlPattern.matcher(url);
        return urlMatcher.find();
    }

    /**
     * Метод вырезает из заданной ссылки адрес сайта в формате http://www.site.com (без слэша в конце)
     *
     * @param link исходная полная ссылка на страницу сайта
     * @return адрес сайта в формате http://www.site.com (без слэша в конце), если он найден, иначе - пустое значение
     */
    public static String getSiteLink(String link) {
        Matcher commandMatcher = siteUrlPattern.matcher(link);
        if (!commandMatcher.find()) {
            return "";
        }
        return commandMatcher.group(1);
    }

    /**
     * Метод определяет расширение файла, на который указывает ссылка, и сравнивает его со списком расширений,
     * не соответствующих HTML файлам
     *
     * @param link ссылка
     * @return True, если ссылка указывает на файл, который с большой долей вероятности не является HTML-страницей
     */
    public static boolean isNonHtmlExtension(String link) {
        for (String extension : nonHtmlExtensions) {
            if (link.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
