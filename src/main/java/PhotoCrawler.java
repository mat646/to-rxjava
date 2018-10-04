import io.reactivex.schedulers.Schedulers;
import model.Photo;
import model.PhotoSize;
import util.PhotoDownloader;
import util.PhotoProcessor;
import util.PhotoSerializer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.reactivex.Observable;

public class PhotoCrawler {

    private static final Logger log = Logger.getLogger(PhotoCrawler.class.getName());

    private final PhotoDownloader photoDownloader;

    private final PhotoSerializer photoSerializer;

    private final PhotoProcessor photoProcessor;

    PhotoCrawler() throws IOException {
        this.photoDownloader = new PhotoDownloader();
        this.photoSerializer = new PhotoSerializer("./photos");
        this.photoProcessor = new PhotoProcessor();
    }

    void resetLibrary() throws IOException {
        photoSerializer.deleteLibraryContents();
    }

    void downloadPhotosForMultipleQueries(List<String> queries) throws IOException {
        List<Observable<Photo>> photoObservables = new ArrayList<>();

        for (String searchQuery : queries) {
            photoObservables.add(photoDownloader.searchForPhotos(searchQuery));
        }

        Observable.merge(photoObservables)
                .compose(this::processPhotos)
                .subscribe(photos -> photos.forEach(photoSerializer::savePhoto));
    }

    public void downloadPhotoExamples() {
        try {
            Observable<Photo> downloadedExamples = photoDownloader.getPhotoExamples().map(photoDownloader::getPhoto);
            downloadedExamples
                    .compose(this::processPhotos)
                    .subscribe(photos -> photos.forEach(photoSerializer::savePhoto));
        } catch (IOException e) {
            log.log(Level.SEVERE, "Downloading photo examples error", e);
        }
    }

    public void downloadPhotosForQuery(String query) {
        photoDownloader.searchForPhotos(query)
                .compose(this::processPhotos)
                .subscribe(photos -> photos.forEach(photoSerializer::savePhoto));
    }

    private Observable<List<Photo>> processPhotos(Observable<Photo> photoObservable) {
        return photoObservable
                .filter(photoProcessor::isPhotoValid)
                .groupBy(PhotoSize::resolve)
                .flatMap(group -> {
                    if (group.getKey() == PhotoSize.MEDIUM) {
                        return group
                                .observeOn(Schedulers.io())
                                .buffer(5, TimeUnit.SECONDS);
                    } else {
                        return group
                                .observeOn(Schedulers.computation())
                                .map(photoProcessor::convertToMiniature)
                                .map(Arrays::asList);
                    }
                });
    }
}
