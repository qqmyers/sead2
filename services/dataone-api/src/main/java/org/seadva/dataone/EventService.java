/*
 * Copyright 2013 The Trustees of Indiana University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author charmadu@umail.iu.edu
*/

package org.seadva.dataone;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URLEncoder;

@Path("/mn/v1/event")
public class EventService {

    public EventService() {
    }


    @GET
    @Produces(MediaType.APPLICATION_XML)
    @Path("{eventId}")
    public Response getLogEvent(@Context HttpServletRequest request,
                                @PathParam("eventId") String eventId) {


        WebResource webResource = Client.create()
                .resource(SeadQueryService.SEAD_DATAONE_URL + "/event")
                .path(URLEncoder.encode(eventId));
        ClientResponse response = webResource
                .accept("application/xml")
                .type("application/xml")
                .get(ClientResponse.class);

        return Response.status(response.getStatus()).entity(response
                .getEntity(new GenericType<String>() {
                })).build();
    }

}
