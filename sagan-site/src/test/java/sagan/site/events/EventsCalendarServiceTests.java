package sagan.site.events;


import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import sagan.SiteProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;


/**
 * Tests for {@link EventsCalendarService}
 */
@RunWith(SpringRunner.class)
@RestClientTest({EventsCalendarService.class, SiteProperties.class})
@TestPropertySource(properties = "sagan.site.events.calendar-uri=http://example.org/ical")
public class EventsCalendarServiceTests {

	private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar");

	@Autowired
	private EventsCalendarService calendarService;

	@Autowired
	private MockRestServiceServer mockServer;

	@Test
	public void shouldFailWithoutCalendarUri() {
		EventsCalendarService service = new EventsCalendarService(new RestTemplateBuilder(), new SiteProperties());
		assertThatThrownBy(() -> service.findEvents(Period.of("2020-01-01", 10)))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("No calendar URI configured, see 'sagan.site.events.calendar-uri'");
	}

	@Test
	public void shouldFailWithMissingCalendar() {
		mockServer.expect(requestTo("http://example.org/ical"))
				.andRespond(MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND));
		assertThatThrownBy(() -> this.calendarService.findEvents(Period.of("2020-01-01", 10)))
				.isInstanceOf(InvalidCalendarException.class).hasMessage("calendar data not available");
	}

	@Test
	public void shouldFailForInvalidCalendar() {
		mockServer.expect(requestTo("http://example.org/ical"))
				.andRespond(withSuccess(getClassPathResource("invalid.ics"), TEXT_CALENDAR));
		assertThatThrownBy(() -> this.calendarService.findEvents(Period.of("2020-01-01", 20)))
				.isInstanceOf(InvalidCalendarException.class).hasMessage("could not parse iCal data");
	}

	@Test
	public void shouldReturnSingleEvent() {
		mockServer.expect(requestTo("http://example.org/ical"))
				.andRespond(withSuccess(getClassPathResource("single-event.ics"), TEXT_CALENDAR));
		List<Event> events = this.calendarService.findEvents(Period.of("2020-05-01", 30));
		assertThat(events).hasSize(1);
		Event event = events.get(0);
		assertThat(event.getSummary()).isEqualTo("Spring IO conference");
		assertThat(event.getStartTime().toString()).isEqualTo("2020-05-14T00:00-07:00[America/Los_Angeles]");
		assertThat(event.getEndTime().toString()).isEqualTo("2020-05-15T09:00-07:00[America/Los_Angeles]");
		assertThat(event.getLocation()).isEqualTo("Barcelona, Spain");
		assertThat(event.getLink().toString()).isEqualTo("https://springio.net");
	}

	@Test
	public void shouldReturnManyEvents() {
		mockServer.expect(requestTo("http://example.org/ical"))
				.andRespond(withSuccess(getClassPathResource("multi-events.ics"), TEXT_CALENDAR));
		List<Event> events = this.calendarService.findEvents(Period.of("2020-05-01", 30));
		assertThat(events).hasSize(2);
		Event event = events.get(0);
		assertThat(event.getSummary()).isEqualTo("Spring @ San Francisco JUG");
		assertThat(event.getStartTime().toString()).isEqualTo("2020-05-11T18:00-07:00[America/Los_Angeles]");
		assertThat(event.getEndTime().toString()).isEqualTo("2020-05-11T21:00-07:00[America/Los_Angeles]");
		assertThat(event.getLocation()).isEqualTo("San Francisco, California");
		assertThat(event.getLink().toString()).isEqualTo("https://spring.io");
	}

	private ClassPathResource getClassPathResource(String path) {
		return new ClassPathResource(path, getClass());
	}
}