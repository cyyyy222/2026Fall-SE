package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface PosterService {
  DownloadResult managementPoster(RequestContext context, UUID eventId);
}
