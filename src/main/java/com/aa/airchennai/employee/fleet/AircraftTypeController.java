
package com.aa.airchennai.employee.fleet;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/employee/fleet/aircraft-types")
public class AircraftTypeController
{
    private static final int MAX_BLOCKS = 4;
    private static final int MAX_ROWS_PER_CABIN = 200;
    private static final int MAX_SEATS_PER_BLOCK = 26;
    private static final int MAX_TOTAL_SEATS = 700;

    private static final String INSERT_SEAT_SQL = """
            INSERT INTO seat_layout (
                aircraft_type_id,
                row_number,
                seat_letter,
                column_position,
                cabin_class,
                seat_position,
                is_exit_row,
                extra_legroom
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public AircraftTypeController(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/add")
    public String showForm(Model model)
    {
        if (!model.containsAttribute("form"))
        {
            model.addAttribute("form", new AircraftTypeForm());
        }

        return "employee/fleet/add-aircraft-type";
    }

    @PostMapping("/add")
    @Transactional
    public String addAircraftType(
            @ModelAttribute("form") AircraftTypeForm form,
            Model model,
            RedirectAttributes redirectAttributes)
    {
        List<String> errors = validate(form);

        if (!errors.isEmpty())
        {
            model.addAttribute("errors", errors);
            return "employee/fleet/add-aircraft-type";
        }

        int economySeats = capacity(form.getEconomy());
        int premiumSeats = capacity(form.getPremiumEconomy());
        int businessSeats = capacity(form.getBusiness());
        int firstSeats = capacity(form.getFirst());

        List<Long> ids;

        try
        {
            ids = jdbcTemplate.query(
                    """
                    INSERT INTO aircraft_type (
                        manufacturer,
                        model,
                        max_range_km,
                        total_seats,
                        economy_seats,
                        premium_economy_seats,
                        business_seats,
                        first_seats,
                        active
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                    ON CONFLICT (manufacturer, model) DO NOTHING
                    RETURNING aircraft_type_id
                    """,
                    (resultSet, rowNumber) ->
                            resultSet.getLong("aircraft_type_id"),
                    form.getManufacturer().trim(),
                    form.getModel().trim(),
                    form.getMaxRangeKm(),
                    form.getTotalSeats(),
                    economySeats,
                    premiumSeats,
                    businessSeats,
                    firstSeats
            );
        }
        catch (DataAccessException exception)
        {
            model.addAttribute(
                    "errors",
                    List.of(
                            "The aircraft type could not be created. "
                                    + "Check the submitted details and try again."
                    )
            );

            return "employee/fleet/add-aircraft-type";
        }

        if (ids.isEmpty())
        {
            model.addAttribute(
                    "errors",
                    List.of("An aircraft type with this manufacturer "
                                    + "and model already exists."
                    )
            );

            return "employee/fleet/add-aircraft-type";
        }

        long aircraftTypeId = ids.get(0);
        List<Object[]> seatRows = new ArrayList<>();
        int nextRowNumber = 0;

        nextRowNumber = appendCabinLayout(
                aircraftTypeId, "FIRST",
                form.getFirst(), nextRowNumber, seatRows
        );

        nextRowNumber = appendCabinLayout(
                aircraftTypeId, "BUSINESS",
                form.getBusiness(), nextRowNumber, seatRows
        );

        nextRowNumber = appendCabinLayout(
                aircraftTypeId, "PREMIUM_ECONOMY",
                form.getPremiumEconomy(), nextRowNumber, seatRows
        );

        appendCabinLayout(
                aircraftTypeId, "ECONOMY",
                form.getEconomy(), nextRowNumber, seatRows
        );

        if (!seatRows.isEmpty())
        {
            jdbcTemplate.batchUpdate(INSERT_SEAT_SQL, seatRows);
        }

        redirectAttributes.addFlashAttribute(
                "createdType",
                form.getManufacturer().trim() + " " + form.getModel().trim()
        );

        redirectAttributes.addFlashAttribute(
                "createdTypeId",
                aircraftTypeId
        );

        return "redirect:/employee/fleet/aircraft-types/add";
    }

    private List<String> validate(AircraftTypeForm form)
    {
        List<String> errors = new ArrayList<>();

        if (form.getManufacturer() == null
                || form.getManufacturer().isBlank()
                || form.getManufacturer().trim().length() > 100)
        {
            errors.add(
                    "Manufacturer is required and must not exceed 100 characters."
            );
        }

        if (form.getModel() == null
                || form.getModel().isBlank()
                || form.getModel().trim().length() > 100)
        {
            errors.add(
                    "Model is required and must not exceed 100 characters."
            );
        }

        if (form.getMaxRangeKm() == null
                || form.getMaxRangeKm() < 1)
        {
            errors.add("Maximum range must be greater than zero.");
        }

        if (form.getTotalSeats() == null
                || form.getTotalSeats() < 1
                || form.getTotalSeats() > MAX_TOTAL_SEATS)
        {
            errors.add(
                    "Total seats must be between 1 and "
                            + MAX_TOTAL_SEATS + "."
            );
        }

        int economy = validateCabin(
                "Economy", form.getEconomy(), errors
        );

        int premium = validateCabin(
                "Premium Economy", form.getPremiumEconomy(), errors
        );

        int business = validateCabin(
                "Business", form.getBusiness(), errors
        );

        int first = validateCabin(
                "First Class", form.getFirst(), errors
        );

        long calculatedTotal =
                (long) economy + premium + business + first;

        if (calculatedTotal < 1 || calculatedTotal > MAX_TOTAL_SEATS)
        {
            errors.add("The calculated seat capacity is outside the allowed range.");
        }
        else if (form.getTotalSeats() != null
                && calculatedTotal != form.getTotalSeats())
        {
            errors.add(
                    "The declared total must equal the calculated total of "
                            + calculatedTotal + " seats."
            );
        }

        return errors;
    }

    private int validateCabin(
            String cabinName,
            CabinLayoutForm cabin,
            List<String> errors)
    {
        if (cabin == null)
        {
            errors.add(cabinName + ": seating details are missing.");
            return 0;
        }

        int rows = number(cabin.getRows());
        int blocks = number(cabin.getBlockCount());

        if (blocks < 0 || blocks > MAX_BLOCKS)
        {
            errors.add(
                    cabinName + ": block count must be between 0 and "
                            + MAX_BLOCKS + "."
            );
            return 0;
        }

        if (blocks == 0)
        {
            if (rows != 0)
            {
                errors.add(
                        cabinName + ": rows must be zero when block count is zero."
                );
            }

            for (int index = 0; index < MAX_BLOCKS; index++)
            {
                if (seatCountAt(cabin, index) != 0)
                {
                    errors.add(
                            cabinName + ": unused blocks must have zero seats."
                    );
                    break;
                }
            }

            return 0;
        }

        if (rows < 1 || rows > MAX_ROWS_PER_CABIN)
        {
            errors.add(
                    cabinName + ": rows must be between 1 and "
                            + MAX_ROWS_PER_CABIN + "."
            );
        }

        int seatsPerRow = 0;

        for (int index = 0; index < MAX_BLOCKS; index++)
        {
            int seats = seatCountAt(cabin, index);

            if (index < blocks)
            {
                if (seats < 1 || seats > MAX_SEATS_PER_BLOCK)
                {
                    errors.add(
                            cabinName + ": each used block must contain 1 to "
                                    + MAX_SEATS_PER_BLOCK + " seats per row."
                    );
                }
                else
                {
                    seatsPerRow += seats;
                }
            }
            else if (seats != 0)
            {
                errors.add(
                        cabinName + ": unused blocks must have zero seats."
                );
            }
        }

        if (seatsPerRow > 26)
        {
            errors.add(
                    cabinName
                            + ": the total number of seats per row cannot exceed 26."
            );
        }

        long calculatedCapacity = (long) rows * seatsPerRow;

        if (calculatedCapacity > MAX_TOTAL_SEATS)
        {
            errors.add(cabinName + ": calculated capacity is too large.");
            return 0;
        }

        return (int) calculatedCapacity;
    }

    private int capacity(CabinLayoutForm cabin)
    {
        return number(cabin.getRows()) * seatsPerRow(cabin);
    }

    private int seatsPerRow(CabinLayoutForm cabin)
    {
        int total = 0;
        int blocks = number(cabin.getBlockCount());

        for (int index = 0; index < blocks; index++)
        {
            total += seatCountAt(cabin, index);
        }

        return total;
    }

    private int appendCabinLayout(
            long aircraftTypeId,
            String cabinClass,
            CabinLayoutForm cabin,
            int previousLastRow,
            List<Object[]> seatRows)
    {
        int rowNumber = previousLastRow;
        int rows = number(cabin.getRows());
        int blocks = number(cabin.getBlockCount());

        for (int row = 1; row <= rows; row++)
        {
            rowNumber++;

            int columnPosition = 1;
            int seatIndex = 0;

            for (int block = 0; block < blocks; block++)
            {
                int seatsInBlock = seatCountAt(cabin, block);

                for (int seatInBlock = 0;
                     seatInBlock < seatsInBlock;
                     seatInBlock++)
                {
                    char seatLetter = (char) ('A' + seatIndex);

                    String seatPosition = determineSeatPosition(
                            blocks, block, seatInBlock, seatsInBlock
                    );

                    seatRows.add(new Object[] {
                            aircraftTypeId,
                            (short) rowNumber,
                            String.valueOf(seatLetter),
                            (short) columnPosition,
                            cabinClass,
                            seatPosition,
                            false,
                            false
                    });

                    columnPosition++;
                    seatIndex++;
                }

                if (block < blocks - 1)
                {
                    // A gap represents the aisle.
                    columnPosition++;
                }
            }
        }

        return rowNumber;
    }

    private String determineSeatPosition(
            int blockCount,
            int blockIndex,
            int seatIndex,
            int seatsInBlock)
    {
        if (blockCount == 1)
        {
            if (seatIndex == 0 || seatIndex == seatsInBlock - 1)
            {
                return "WINDOW";
            }

            return "MIDDLE";
        }

        if (blockIndex == 0 && seatIndex == 0)
        {
            return "WINDOW";
        }

        if (blockIndex == blockCount - 1
                && seatIndex == seatsInBlock - 1)
        {
            return "WINDOW";
        }

        if (blockIndex > 0 && seatIndex == 0)
        {
            return "AISLE";
        }

        if (blockIndex < blockCount - 1
                && seatIndex == seatsInBlock - 1)
        {
            return "AISLE";
        }

        return "MIDDLE";
    }

    private int seatCountAt(CabinLayoutForm cabin, int index)
    {
        List<Integer> seats = cabin.getSeatsPerBlock();

        if (seats == null || index >= seats.size() || seats.get(index) == null)
        {
            return 0;
        }

        return seats.get(index);
    }

    private int number(Integer value)
    {
        return value == null ? 0 : value;
    }

    public static class AircraftTypeForm
    {
        private String manufacturer;
        private String model;
        private Integer maxRangeKm;
        private Integer totalSeats;

        private CabinLayoutForm economy = new CabinLayoutForm();
        private CabinLayoutForm premiumEconomy = new CabinLayoutForm();
        private CabinLayoutForm business = new CabinLayoutForm();
        private CabinLayoutForm first = new CabinLayoutForm();

        public String getManufacturer()
        {
            return manufacturer;
        }

        public void setManufacturer(String manufacturer)
        {
            this.manufacturer = manufacturer;
        }

        public String getModel()
        {
            return model;
        }

        public void setModel(String model)
        {
            this.model = model;
        }

        public Integer getMaxRangeKm()
        {
            return maxRangeKm;
        }

        public void setMaxRangeKm(Integer maxRangeKm)
        {
            this.maxRangeKm = maxRangeKm;
        }

        public Integer getTotalSeats()
        {
            return totalSeats;
        }

        public void setTotalSeats(Integer totalSeats)
        {
            this.totalSeats = totalSeats;
        }

        public CabinLayoutForm getEconomy()
        {
            return economy;
        }

        public void setEconomy(CabinLayoutForm economy)
        {
            this.economy = economy;
        }

        public CabinLayoutForm getPremiumEconomy()
        {
            return premiumEconomy;
        }

        public void setPremiumEconomy(CabinLayoutForm premiumEconomy)
        {
            this.premiumEconomy = premiumEconomy;
        }

        public CabinLayoutForm getBusiness()
        {
            return business;
        }

        public void setBusiness(CabinLayoutForm business)
        {
            this.business = business;
        }

        public CabinLayoutForm getFirst()
        {
            return first;
        }

        public void setFirst(CabinLayoutForm first)
        {
            this.first = first;
        }
    }

    public static class CabinLayoutForm
    {
        private Integer rows = 0;
        private Integer blockCount = 0;
        private List<Integer> seatsPerBlock =
                new ArrayList<>(List.of(0, 0, 0, 0));

        public Integer getRows()
        {
            return rows;
        }

        public void setRows(Integer rows)
        {
            this.rows = rows;
        }

        public Integer getBlockCount()
        {
            return blockCount;
        }

        public void setBlockCount(Integer blockCount)
        {
            this.blockCount = blockCount;
        }

        public List<Integer> getSeatsPerBlock()
        {
            return seatsPerBlock;
        }

        public void setSeatsPerBlock(List<Integer> seatsPerBlock)
        {
            if (seatsPerBlock != null)
            {
                this.seatsPerBlock = seatsPerBlock;
            }
        }
    }
}
