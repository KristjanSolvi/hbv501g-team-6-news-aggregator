package is.hi.team6.newsaggregator.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


import is.hi.team6.newsaggregator.dto.NewsSourceRequest;
import is.hi.team6.newsaggregator.dto.NewsSourceResponse;
import is.hi.team6.newsaggregator.service.NewsSourceService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/newssources")
public class NewsSourceController {
    private final NewsSourceService sources;

    public NewsSourceController(NewsSourceService sources) {
        this.sources = sources;
    }
    @GetMapping("/sources")
    @ResponseStatus(HttpStatus.OK)
    public List<NewsSourceResponse> getSources() {
        return sources.getSources();
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public NewsSourceResponse createSource(@Valid @RequestBody NewsSourceRequest request){
        return sources.createSource(request);
    }

    @PutMapping ("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public NewsSourceResponse updateSource(@Valid @PathVariable UUID id,
        @RequestBody  NewsSourceRequest request){
        return sources.updateSource(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(@PathVariable UUID id){
        sources.deleteSource(id);
    }
}
