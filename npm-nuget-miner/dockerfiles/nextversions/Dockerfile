FROM python:3.8.2-alpine3.11	

ENV PYTHONUNBUFFERED=1

RUN pip install neo4j

COPY ./dependencygraphutils /usr/app/nextrelations/dependencygraphutils
COPY ./postprocessing/ /usr/app/nextrelations/postprocessing
COPY ./nextversions.py /usr/app/nextrelations/nextversions.py

WORKDIR /usr/app/nextrelations

ENTRYPOINT python /usr/app/nextrelations/nextversions.py