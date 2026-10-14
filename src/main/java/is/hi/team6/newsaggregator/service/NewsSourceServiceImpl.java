package is.hi.team6.newsaggregator.service;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import is.hi.team6.newsaggregator.dto.NewsSourceRequest;
import is.hi.team6.newsaggregator.dto.NewsSourceResponse;
import is.hi.team6.newsaggregator.model.NewsSource;
import is.hi.team6.newsaggregator.repository.NewsSourceRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;


@Service 
public class NewsSourceServiceImpl implements NewsSourceService {
    private final NewsSourceRepository repository;

    public NewsSourceServiceImpl(NewsSourceRepository repository){
        this.repository = repository;
    }
    @Override
    @Transactional(readOnly = true)
    public List<NewsSourceResponse> getSources(){
        return repository.findAll().stream().map(
            source -> new NewsSourceResponse(
                source.getId(), source.getName(), source.getUrl()))
                .toList();
    };

    @Override
    public NewsSourceResponse createSource(NewsSourceRequest request){
        if (request.name().isEmpty() || request.url().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing name or url");
        }
        NewsSource source = new NewsSource(request.name(), request.url());
        source = repository.save(source);
        return new NewsSourceResponse(source.getId(),source.getName(),source.getUrl());
    };

    @Override 
    public NewsSourceResponse updateSource(Long id, NewsSourceRequest request){
        Optional<NewsSource> maybeSource = repository.findById(id);
        NewsSource source;
        if (maybeSource.isEmpty()) {
            throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "News source not found");         
        }
        source = maybeSource.get();
        if(request.name().isEmpty() || request.url().isEmpty()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing name or url");
        }
        source.update(request.name(), request.url());
        source = repository.save(source);
        return new NewsSourceResponse(source.getId(), source.getName(), source.getUrl());
    };

    @Override 
    public void deleteSource(Long id){
        if (id == null) {
            return;
        }
        repository.deleteById(id);
    };

    @Override 
    public NewsSource getSource(Long id){
        Optional<NewsSource> temp = repository.findById(id);
        if (temp.isEmpty()) { //if the source doesnt exist returns null? kannski að skila tómum seinna?
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing request id");
        }
        return temp.get();
    };
}
