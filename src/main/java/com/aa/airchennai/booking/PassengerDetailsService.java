package com.aa.airchennai.booking;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * Keeps passenger details in the customer's HTTP session while the booking flow is in progress.
 * A permanent booking/passenger row is created only when the booking/payment workflow is completed.
 */
@Service
public class PassengerDetailsService {
    public static final String SESSION_ATTRIBUTE = "airChennaiPassengerDetails";

    public void save(HttpSession session, PassengerDetailsForm form) {
        PassengerDetailsForm saved = new PassengerDetailsForm();
        saved.setFirstName(form.getFirstName());
        saved.setLastName(form.getLastName());
        saved.setDateOfBirth(form.getDateOfBirth());
        saved.setGender(form.getGender());
        saved.setNationality(form.getNationality());
        saved.setPassengerType(form.getPassengerType());
        session.setAttribute(SESSION_ATTRIBUTE, saved);
    }

    public PassengerDetailsForm get(HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        return value instanceof PassengerDetailsForm form ? form : null;
    }

    public boolean exists(HttpSession session) {
        return get(session) != null;
    }

    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_ATTRIBUTE);
    }
}
