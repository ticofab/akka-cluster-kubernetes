var stats = [
    ['Time', 'Queue size', 'Workers', 'Burndown rate', 'New jobs per interval']
];

google.charts.load('current', {'packages': ['corechart']});

function drawChart(input) {
    const data = google.visualization.arrayToDataTable(input);

    const options = {
        legend: {position: 'bottom'},
        vAxis: {
            ticks: [-20, 0, 20, 40],
            viewWindow: {
                min: -20,
                max: 40
            }
        },
        hAxis: {
            textPosition: 'none'
        }
    };

    const chart = new google.visualization.LineChart(document.getElementById('curve_chart'));

    chart.draw(data, options);
}

function randInt() {
    return Math.floor(Math.random() * 1000) + 1;
}

function randomDrawChart() {
    const input = [
        ['Year', 'Sales', 'Expenses'],
        ['2004', randInt(), randInt()],
        ['2005', 1170, 460],
        ['2006', randInt(), randInt()],
        ['2007', 1030, 540]
    ];

    drawChart(input);
}