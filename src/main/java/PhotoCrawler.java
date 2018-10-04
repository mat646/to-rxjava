import io.reactivex.schedulers.Schedulers;
import model.Photo;
import model.PhotoSize;
import util.PhotoDownloader;
import util.PhotoProcessor;
import util.PhotoSerializer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import io.reactivex.Observable;

public class PhotoCrawler {

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
            photoObservables.add(photoDownloader
                    .searchForPhotos(searchQuery)
                    .subscribeOn(Schedulers.io()));
        }

        Observable.merge(photoObservables)
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
