FROM python:3.8.2-alpine3.11	

ENV PYTHONUNBUFFERED=1

RUN pip install neo4j
RUN pip install requests

COPY ./dependencygraphutils/ /usr/app/nuget_crawler/dependencygraphutils
COPY ./nugetanalysis/ /usr/app/nuget_crawler/nugetanalysis
COPY ./nugetentry.py /usr/app/nuget_crawler/nugetentry.py

WORKDIR /usr/app/nuget_crawler

ENTRYPOINT python /usr/app/nuget_crawler/nugetentry.py