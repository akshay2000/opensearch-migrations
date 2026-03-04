FROM mirror.gcr.io/library/solr:8

USER root

# Create a custom configset based on _default, then overlay our schema
RUN cp -r /opt/solr/server/solr/configsets/_default /opt/solr/server/solr/configsets/imdb_movies \
    && rm -f /opt/solr/server/solr/configsets/imdb_movies/conf/managed-schema \
    && chown -R solr:solr /opt/solr/server/solr/configsets/imdb_movies

COPY --chown=solr:solr data/schema.xml /opt/solr/server/solr/configsets/imdb_movies/conf/schema.xml

# Copy the data file for seeding
COPY --chown=solr:solr data/imdb_movies_solr.json /opt/seed-data/imdb_movies_solr.json

# Copy the seed script
COPY --chown=solr:solr seed.sh /opt/seed-data/seed.sh
RUN chmod +x /opt/seed-data/seed.sh

USER solr

# Launch seed script in background, then exec solr-precreate as PID 1
CMD /opt/seed-data/seed.sh & exec solr-precreate imdb_movies /opt/solr/server/solr/configsets/imdb_movies
